package com.memphisreo.platform;

import com.memphisreo.listing.SaleForm;
import com.memphisreo.platform.property.PropertyEditorDtos.Price;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyPayload;
import com.memphisreo.platform.publicsite.PublicDtos.AgencyInfo;
import com.memphisreo.platform.publicsite.PublicDtos.InquiryRequest;
import com.memphisreo.platform.publicsite.PublicDtos.PublicCard;
import com.memphisreo.platform.publicsite.PublicDtos.PublicDetails;
import com.memphisreo.platform.publicsite.PublicDtos.SearchResult;
import com.memphisreo.platform.sale.SaleDtos.SaleView;
import com.memphisreo.platform.sale.SaleDtos.TimelineEntry;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Публічна сторінка агенції: лише ACTIVE, фільтри, прихована адреса, заявка. */
class PublicSiteIT extends AbstractIntegrationTest {

    private String slug;
    private String token;
    private PropertyDetails listed;
    private PropertyDetails draftOnly;

    @BeforeEach
    void agency() {
        slug = "pub-" + UUID.randomUUID().toString().substring(0, 8);
        register(slug, "admin@" + slug + ".ua", "Password123!", "UA");
        token = login("admin@" + slug + ".ua", "Password123!");
        listed = createProperty(token);
        listProperty(token, listed.id());
        draftOnly = createProperty(token);
    }

    @Test
    void onlyActiveListings_areVisible_andAgencyInfoIsPublic() {
        SearchResult result = search("");
        assertThat(result.items()).extracting(PublicCard::id).containsExactly(listed.id());
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).price()).isEqualByComparingTo("95000");
        assertThat(result.items().get(0).street()).isEqualTo("Khreshchatyk");

        AgencyInfo info = restTemplate.getForEntity("/api/public/agencies/" + slug, AgencyInfo.class).getBody();
        assertThat(info.activeListings()).isEqualTo(1);

        assertThat(restTemplate.getForEntity("/api/public/agencies/" + slug + "/properties/" + draftOnly.id(), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity("/api/public/agencies/nope-" + slug, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void filters_narrowResults() {
        PropertyForm land = new PropertyForm(Property.Type.LAND, null, "Ділянка", null, null, null, null, new BigDecimal("1000"),
                null, null, null, null, null, null, null, null, null, null, Property.LandPurpose.RESIDENTIAL, null, null, null,
                null, List.of(), null, apartmentPayload().property().address());
        PropertyDetails plot = createProperty(token, new PropertyPayload(land, new Price(new BigDecimal("28000"), "USD")));
        listProperty(token, plot.id());

        assertThat(search("").total()).isEqualTo(2);
        assertThat(search("type=LAND").items()).extracting(PublicCard::id).containsExactly(plot.id());
        assertThat(search("priceMax=50000").items()).extracting(PublicCard::id).containsExactly(plot.id());
        assertThat(search("roomsMin=2").items()).extracting(PublicCard::id).containsExactly(listed.id());
        assertThat(search("features=BALCONY").items()).extracting(PublicCard::id).containsExactly(listed.id());
        assertThat(search("q=Test").items()).extracting(PublicCard::id).containsExactly(listed.id());
        assertThat(search("sort=priceAsc").items()).extracting(PublicCard::id).containsExactly(plot.id(), listed.id());
        assertThat(search("district=Pecherskyi").total()).isEqualTo(2);
        assertThat(search("").districts()).containsExactly("Pecherskyi");
    }

    @Test
    void hiddenAddress_dropsStreetAndRoundsCoordinates() {
        SaleForm hide = new SaleForm(null, null, null, null, null, null, null, null, true);
        restTemplate.exchange("/api/properties/" + listed.id() + "/sale", HttpMethod.PUT, authed(token, hide), SaleView.class);

        PublicDetails details = details(listed.id());
        assertThat(details.approximateLocation()).isTrue();
        assertThat(details.street()).isNull();
        assertThat(details.houseNumber()).isNull();
        assertThat(details.city()).isEqualTo("Kyiv");
        assertThat(details.latitude()).isNotEqualTo(50.4501).isCloseTo(50.4501, org.assertj.core.data.Offset.offset(0.003));
        assertThat(details.agents()).hasSize(1);
        assertThat(details.agency().name()).startsWith("Agency ");
        assertThat(details.photos()).isEmpty();
    }

    @Test
    void inquiry_createsLeadSourceAndTimelineEvent_andValidatesContact() {
        ResponseEntity<String> missing = restTemplate.postForEntity(inquiriesPath(listed.id()),
                new InquiryRequest("Покупець", null, null, "?"), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody()).contains("contactRequired");

        ResponseEntity<String> ok = restTemplate.postForEntity(inquiriesPath(listed.id()),
                new InquiryRequest("Покупець", "+380501112233", null, "Коли можна подивитись?"), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<TimelineEntry> timeline = List.of(restTemplate.exchange("/api/properties/" + listed.id() + "/timeline",
                HttpMethod.GET, authed(token), TimelineEntry[].class).getBody());
        assertThat(timeline.get(0).type()).isEqualTo("INQUIRY_RECEIVED");
        assertThat(timeline.get(0).actor()).isNull();

        assertThat(restTemplate.postForEntity(inquiriesPath(draftOnly.id()),
                new InquiryRequest("X", "+380", null, null), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private SearchResult search(String query) {
        return restTemplate.exchange("/api/public/agencies/" + slug + "/properties?" + query, HttpMethod.GET,
                HttpEntity.EMPTY, SearchResult.class).getBody();
    }

    private PublicDetails details(UUID id) {
        return restTemplate.getForEntity("/api/public/agencies/" + slug + "/properties/" + id, PublicDetails.class).getBody();
    }

    private String inquiriesPath(UUID id) {
        return "/api/public/agencies/" + slug + "/properties/" + id + "/inquiries";
    }
}
