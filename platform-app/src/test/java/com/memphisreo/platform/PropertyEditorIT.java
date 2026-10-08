package com.memphisreo.platform;

import com.memphisreo.listing.Listing;
import com.memphisreo.platform.property.PropertyEditorDtos.Price;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyCard;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyPayload;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Форма об'єкта: чернетка з автозбереженням → завершення з перевіркою обов'язкових полів за типом. */
class PropertyEditorIT extends AbstractIntegrationTest {

    private String token;

    @BeforeEach
    void agency() {
        String slug = "editor-" + UUID.randomUUID().toString().substring(0, 8);
        register(slug, "admin@" + slug + ".ua", "Password123!", "UA");
        token = login("admin@" + slug + ".ua", "Password123!");
    }

    @Test
    void draftWithOnlyType_isSaved_andReportsWhatIsMissing() {
        PropertyDetails draft = createProperty(token, new PropertyPayload(onlyType(Property.Type.LAND), null));

        assertThat(draft.status()).isEqualTo(Property.Status.DRAFT);
        assertThat(draft.missing()).extracting(e -> e.field())
                .containsExactlyInAnyOrder("address.city", "landAreaSqm", "landPurpose", "price.amount");
    }

    @Test
    void completingIncompleteDraft_returns400WithFieldErrors() {
        PropertyDetails draft = createProperty(token, new PropertyPayload(onlyType(Property.Type.APARTMENT), null));

        ResponseEntity<String> response = restTemplate.exchange("/api/properties/" + draft.id() + "/complete",
                HttpMethod.POST, authed(token), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"field\":\"rooms\"", "\"field\":\"floor\"", "\"field\":\"price.amount\"");
    }

    @Test
    void fullApartment_completes_andPriceBecomesDraftListing() {
        PropertyDetails draft = createProperty(token);
        assertThat(draft.missing()).isEmpty();
        assertThat(draft.price().amount()).isEqualByComparingTo("95000");
        assertThat(draft.price().currency()).isEqualTo("USD");

        PropertyDetails completed = restTemplate.exchange("/api/properties/" + draft.id() + "/complete",
                HttpMethod.POST, authed(token), PropertyDetails.class).getBody();
        assertThat(completed.status()).isEqualTo(Property.Status.ACTIVE);

        Listing[] listings = restTemplate.exchange("/api/listings", HttpMethod.GET, authed(token), Listing[].class).getBody();
        assertThat(listings).hasSize(1);
        assertThat(listings[0].getStatus()).isEqualTo(Listing.Status.DRAFT);
        assertThat(listings[0].getPropertyId()).isEqualTo(draft.id());
    }

    @Test
    void autosave_updatesFieldsAndPrice_andRoundTripsAddressAndFeatures() {
        PropertyDetails draft = createProperty(token);
        PropertyForm changed = withRoomsAndFeatures(apartmentPayload().property(), 3,
                List.of(Property.Feature.SHELTER, Property.Feature.BACKUP_POWER));

        PropertyDetails saved = restTemplate.exchange("/api/properties/" + draft.id(), HttpMethod.PUT,
                authed(token, new PropertyPayload(changed, new Price(new BigDecimal("99000"), "USD"))),
                PropertyDetails.class).getBody();

        assertThat(saved.property().rooms()).isEqualTo(3);
        assertThat(saved.property().features()).containsExactly(Property.Feature.SHELTER, Property.Feature.BACKUP_POWER);
        assertThat(saved.property().address().latitude()).isEqualTo(50.4501);
        assertThat(saved.price().amount()).isEqualByComparingTo("99000");

        PropertyCard[] cards = restTemplate.exchange("/api/properties", HttpMethod.GET, authed(token),
                PropertyCard[].class).getBody();
        assertThat(cards).hasSize(1);
        assertThat(cards[0].price()).isEqualByComparingTo("99000");
        assertThat(cards[0].city()).isEqualTo("Kyiv");
    }

    @Test
    void invalidValues_areRejectedEvenInDraft() {
        PropertyForm floorAboveTotal = withFloors(apartmentPayload().property(), 12, 9);
        ResponseEntity<String> response = restTemplate.exchange("/api/properties", HttpMethod.POST,
                authed(token, new PropertyPayload(floorAboveTotal, null)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("floorAboveTotal");
    }

    @Test
    void activeProperty_cannotLoseRequiredFieldsOnAutosave() {
        PropertyDetails draft = createProperty(token);
        restTemplate.exchange("/api/properties/" + draft.id() + "/complete", HttpMethod.POST, authed(token), String.class);

        PropertyForm withoutRooms = withRoomsAndFeatures(apartmentPayload().property(), null, List.of());
        ResponseEntity<String> response = restTemplate.exchange("/api/properties/" + draft.id(), HttpMethod.PUT,
                authed(token, new PropertyPayload(withoutRooms, apartmentPayload().price())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"field\":\"rooms\"");
    }

    @Test
    void formSchema_exposesRequiredFieldsPerType() {
        String schema = restTemplate.exchange("/api/properties/form-schema", HttpMethod.GET, authed(token),
                String.class).getBody();
        assertThat(schema).contains("\"LAND\":[\"address.city\",\"landAreaSqm\",\"landPurpose\"]");
    }

    @Test
    void geocodingProxy_returnsNormalizedPlaces() {
        String body = restTemplate.exchange("/api/geo/search?q=Хрещатик", HttpMethod.GET, authed(token),
                String.class).getBody();
        assertThat(body).contains("\"city\":\"Київ\"", "\"houseNumber\":\"22\"");
    }

    private static PropertyForm onlyType(Property.Type type) {
        return new PropertyForm(type, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static PropertyForm withRoomsAndFeatures(PropertyForm f, Integer rooms, List<Property.Feature> features) {
        return new PropertyForm(f.type(), f.market(), f.title(), f.description(), f.areaSqm(), f.livingAreaSqm(),
                f.kitchenAreaSqm(), f.landAreaSqm(), rooms, f.bedrooms(), f.bathrooms(), f.floor(), f.totalFloors(),
                f.yearBuilt(), f.ceilingHeightM(), f.wallMaterial(), f.condition(), f.heating(), f.landPurpose(),
                f.commercialType(), f.cadastralNumber(), f.hasElevator(), f.parkingSpaces(), features, f.unitNumber(),
                f.address());
    }

    private static PropertyForm withFloors(PropertyForm f, Integer floor, Integer totalFloors) {
        return new PropertyForm(f.type(), f.market(), f.title(), f.description(), f.areaSqm(), f.livingAreaSqm(),
                f.kitchenAreaSqm(), f.landAreaSqm(), f.rooms(), f.bedrooms(), f.bathrooms(), floor, totalFloors,
                f.yearBuilt(), f.ceilingHeightM(), f.wallMaterial(), f.condition(), f.heating(), f.landPurpose(),
                f.commercialType(), f.cadastralNumber(), f.hasElevator(), f.parkingSpaces(), f.features(),
                f.unitNumber(), f.address());
    }
}
