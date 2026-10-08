package com.memphisreo.crm;

import java.math.BigDecimal;
import java.util.List;

public record RequirementForm(
        String propertyType,
        Integer roomsMin,
        Integer roomsMax,
        BigDecimal priceMin,
        BigDecimal priceMax,
        String currency,
        BigDecimal areaMin,
        List<String> districts,
        String market,
        List<String> mustHave,
        String notes,
        Boolean active
) {
}
