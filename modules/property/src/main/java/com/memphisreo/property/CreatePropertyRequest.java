package com.memphisreo.property;

import java.math.BigDecimal;

public record CreatePropertyRequest(
        Property.Type type,
        String unitNumber,
        BigDecimal areaSqm,
        Integer rooms,
        Integer floor,
        Integer totalFloors,
        Integer yearBuilt,
        String attributesJson,
        String countryCode,
        String region,
        String city,
        String district,
        String street,
        String houseNumber,
        String postalCode,
        Double latitude,
        Double longitude
) {
}
