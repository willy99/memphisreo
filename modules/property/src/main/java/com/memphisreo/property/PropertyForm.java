package com.memphisreo.property;

import java.math.BigDecimal;
import java.util.List;

/**
 * Стан форми об'єкта: усе, крім типу, може бути порожнім — це чернетка
 * (автозбереження). Обов'язковість перевіряється лише при завершенні
 * ({@link PropertyService#complete}), за правилами {@link PropertyRules}.
 */
public record PropertyForm(
        Property.Type type,
        Property.Market market,
        String title,
        String description,
        BigDecimal areaSqm,
        BigDecimal livingAreaSqm,
        BigDecimal kitchenAreaSqm,
        BigDecimal landAreaSqm,
        Integer rooms,
        Integer bedrooms,
        Integer bathrooms,
        Integer floor,
        Integer totalFloors,
        Integer yearBuilt,
        BigDecimal ceilingHeightM,
        Property.WallMaterial wallMaterial,
        Property.Condition condition,
        Property.Heating heating,
        Property.LandPurpose landPurpose,
        Property.CommercialType commercialType,
        String cadastralNumber,
        Boolean hasElevator,
        Integer parkingSpaces,
        List<Property.Feature> features,
        String unitNumber,
        AddressForm address
) {

    public record AddressForm(
            String countryCode,
            String region,
            String city,
            String district,
            String street,
            String houseNumber,
            String postalCode,
            String complexName,
            Double latitude,
            Double longitude,
            Address.GeocodeSource geocodeSource
    ) {
    }
}
