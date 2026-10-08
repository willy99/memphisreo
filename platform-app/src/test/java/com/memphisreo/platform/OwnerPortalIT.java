package com.memphisreo.platform;

import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientForm;
import com.memphisreo.listing.SaleForm;
import com.memphisreo.platform.owner.OwnerPortalService.OwnerReport;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;
import com.memphisreo.platform.sale.SaleDtos.OwnerLink;
import com.memphisreo.platform.sale.SaleDtos.SaleView;
import com.memphisreo.platform.sale.SaleDtos.TimelineEntry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Власник бачить лише свої об'єкти і лише owner_visible події; посилання без логіну. */
class OwnerPortalIT extends AbstractIntegrationTest {

    @Test
    void ownerSeesOnlyOwnProperties_withOwnerVisibleTimeline() {
        String slug = "owner-" + UUID.randomUUID().toString().substring(0, 8);
        register(slug, "admin@" + slug + ".ua", "Password123!", "UA");
        String token = login("admin@" + slug + ".ua", "Password123!");

        Client owner = restTemplate.exchange("/api/clients", HttpMethod.POST,
                authed(token, new ClientForm("Сергій", "Литвиненко", null, "+380970000000", Client.Source.WALK_IN, null)),
                Client.class).getBody();
        PropertyDetails mine = createProperty(token);
        PropertyDetails someoneElses = createProperty(token);
        restTemplate.exchange("/api/properties/" + mine.id() + "/sale", HttpMethod.PUT,
                authed(token, new SaleForm(null, null, owner.getId(), null, null, null, null, null, false)), SaleView.class);
        listProperty(token, mine.id());
        listProperty(token, someoneElses.id());
        // Внутрішня подія (не для власника).
        restTemplate.exchange("/api/properties/" + mine.id() + "/agents", HttpMethod.PUT,
                authed(token, new com.memphisreo.platform.sale.SaleDtos.AgentsRequest(mine.property() == null ? null : currentAgentId(token), List.of())),
                String.class);

        OwnerLink link = restTemplate.exchange("/api/clients/" + owner.getId() + "/owner-link", HttpMethod.POST,
                authed(token), OwnerLink.class).getBody();
        String ownerToken = link.url().substring(link.url().lastIndexOf('/') + 1);

        OwnerReport report = restTemplate.getForEntity("/api/public/owner/" + ownerToken, OwnerReport.class).getBody();
        assertThat(report.ownerFirstName()).isEqualTo("Сергій");
        assertThat(report.properties()).hasSize(1);
        assertThat(report.properties().get(0).id()).isEqualTo(mine.id());
        List<TimelineEntry> timeline = report.properties().get(0).timeline();
        assertThat(timeline).extracting(TimelineEntry::type).contains("LISTED", "SELLER_SET").doesNotContain("AGENTS_CHANGED");
        assertThat(timeline).allMatch(TimelineEntry::ownerVisible);

        ResponseEntity<String> bogus = restTemplate.getForEntity("/api/public/owner/not-a-token", String.class);
        assertThat(bogus.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID currentAgentId(String token) {
        return restTemplate.exchange("/api/agents/me", HttpMethod.GET, authed(token),
                com.memphisreo.agent.Agent.class).getBody().getId();
    }
}
