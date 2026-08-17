package com.memphisreo.listing;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateListingRequest(
        UUID propertyId,
        Listing.DealType dealType,
        BigDecimal price,
        String currency
) {
}
