package com.memphisreo.property;

import com.memphisreo.common.ValidationException.FieldError;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyRulesTest {

    @Test
    void landRequiresAreaAndPurpose_butNoStreetOrRooms() {
        Property land = new Property();
        land.setType(Property.Type.LAND);
        Address address = new Address();
        address.setCity("Бровари");

        assertThat(PropertyRules.missingRequired(land, address)).extracting(FieldError::field)
                .containsExactly("landAreaSqm", "landPurpose");
    }

    @Test
    void apartmentRequiresFloorsRoomsAndExactAddress() {
        Property apartment = new Property();
        apartment.setType(Property.Type.APARTMENT);

        assertThat(PropertyRules.missingRequired(apartment, new Address())).extracting(FieldError::field)
                .contains("address.street", "address.houseNumber", "rooms", "floor", "totalFloors", "market");
    }

    @Test
    void valuesAreCheckedForConsistency() {
        List<FieldError> errors = PropertyRules.validateValues(form(new BigDecimal("50"), new BigDecimal("60"), 10, 9, "123"));
        assertThat(errors).extracting(FieldError::code)
                .containsExactlyInAnyOrder("exceedsTotalArea", "floorAboveTotal", "cadastralFormat");

        assertThat(PropertyRules.validateValues(form(new BigDecimal("50"), new BigDecimal("30"), 3, 9,
                "3222486200:03:001:0001"))).isEmpty();
    }

    private static PropertyForm form(BigDecimal area, BigDecimal living, Integer floor, Integer total, String cadastral) {
        return new PropertyForm(Property.Type.APARTMENT, null, null, null, area, living, null, null, null, null, null,
                floor, total, null, null, null, null, null, null, null, cadastral, null, null, null, null, null);
    }
}
