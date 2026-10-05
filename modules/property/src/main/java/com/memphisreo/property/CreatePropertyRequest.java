package com.memphisreo.property;

import java.math.BigDecimal;

public record CreatePropertyRequest(
        Property.Type type,
        String unitNumber,
        BigDecimal areaSqm,
        BigDecimal landAreaSqm,
        Integer rooms,
        Integer bedrooms,
        Integer bathrooms,
        Integer floor,
        Integer totalFloors,
        Integer yearBuilt,
        Boolean hasElevator,
        Integer parkingSpaces,
        String description,
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
