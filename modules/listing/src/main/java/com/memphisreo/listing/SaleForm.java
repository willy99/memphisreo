package com.memphisreo.listing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Вкладка "Продаж": усе, що агент редагує, крім статусу (він змінюється діями). */
public record SaleForm(
        BigDecimal price,
        String currency,
        UUID sellerClientId,
        Listing.MandateType mandateType,
        LocalDate mandateValidUntil,
        BigDecimal commissionPercent,
        BigDecimal commissionFixed,
        String accessNotes,
        boolean hideExactAddress
) {
}
