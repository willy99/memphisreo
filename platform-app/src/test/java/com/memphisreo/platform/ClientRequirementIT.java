package com.memphisreo.platform;

import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientForm;
import com.memphisreo.crm.RequirementForm;
import com.memphisreo.platform.crm.ClientDtos.ClientDetails;
import com.memphisreo.platform.crm.ClientDtos.NoteRequest;
import com.memphisreo.platform.crm.MatchingService.ClientMatch;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;
import com.memphisreo.platform.sale.SaleDtos.TimelineEntry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Запит клієнта, підбір об'єкт↔клієнт, журнал контактів. docs/sales-workflow.md §3.5. */
class ClientRequirementIT extends AbstractIntegrationTest {

    @Test
    void requirementMatchesActiveListingsBothWays_andJournalIsRecorded() {
        String slug = "req-" + UUID.randomUUID().toString().substring(0, 8);
        register(slug, "admin@" + slug + ".ua", "Password123!", "UA");
        String token = login("admin@" + slug + ".ua", "Password123!");
        PropertyDetails listed = createProperty(token);   // 2-кімн., 54.5 м², Pecherskyi, 95 000 USD, BALCONY
        listProperty(token, listed.id());
        createProperty(token);                            // чернетка — не у продажу, не має підбиратись

        Client buyer = restTemplate.exchange("/api/clients", HttpMethod.POST,
                authed(token, new ClientForm("Марина", "Коваленко", null, "+380670000000", Client.Source.ADVERTISEMENT, null)),
                Client.class).getBody();

        // Запит підходить: тип, 2 кімнати, до 120k, район, балкон.
        restTemplate.exchange("/api/clients/" + buyer.getId() + "/requirement", HttpMethod.PUT,
                authed(token, new RequirementForm("APARTMENT", 2, 3, null, new BigDecimal("120000"), "USD", null,
                        List.of("pecherskyi"), null, List.of("BALCONY"), null, true)), String.class);

        ClientDetails details = restTemplate.exchange("/api/clients/" + buyer.getId() + "/details", HttpMethod.GET,
                authed(token), ClientDetails.class).getBody();
        assertThat(details.requirement().getDistricts()).containsExactly("pecherskyi");
        assertThat(details.matches()).hasSize(1);
        assertThat(details.matches().get(0).property().id()).isEqualTo(listed.id());
        assertThat(details.matches().get(0).matched()).containsExactly("type", "rooms", "price", "district", "features");

        ClientMatch[] clients = restTemplate.exchange("/api/properties/" + listed.id() + "/matching-clients", HttpMethod.GET,
                authed(token), ClientMatch[].class).getBody();
        assertThat(clients).extracting(ClientMatch::clientId).containsExactly(buyer.getId());

        // Бюджет менший за ціну → не підходить.
        restTemplate.exchange("/api/clients/" + buyer.getId() + "/requirement", HttpMethod.PUT,
                authed(token, new RequirementForm("APARTMENT", null, null, null, new BigDecimal("90000"), "USD", null,
                        List.of(), null, List.of(), null, true)), String.class);
        assertThat(restTemplate.exchange("/api/clients/" + buyer.getId() + "/details", HttpMethod.GET, authed(token),
                ClientDetails.class).getBody().matches()).isEmpty();

        // Некоректний діапазон → 400.
        assertThat(restTemplate.exchange("/api/clients/" + buyer.getId() + "/requirement", HttpMethod.PUT,
                authed(token, new RequirementForm(null, 3, 2, null, null, "USD", null, null, null, null, null, true)),
                String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Журнал контактів.
        List<TimelineEntry> timeline = List.of(restTemplate.exchange("/api/clients/" + buyer.getId() + "/notes", HttpMethod.POST,
                authed(token, new NoteRequest("CALL", "Домовились про показ у суботу")), TimelineEntry[].class).getBody());
        assertThat(timeline.get(0).type()).isEqualTo("CONTACT_CALL");
        assertThat(timeline.get(0).payload()).containsEntry("note", "Домовились про показ у суботу");
        assertThat(timeline).extracting(TimelineEntry::type).contains("REQUIREMENT_UPDATED", "CLIENT_CREATED");
    }
}
