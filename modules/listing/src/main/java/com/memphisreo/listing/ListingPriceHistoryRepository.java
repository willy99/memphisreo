package com.memphisreo.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingPriceHistoryRepository extends JpaRepository<ListingPriceHistory, UUID> {

    List<ListingPriceHistory> findByListingIdOrderByChangedAtDesc(UUID listingId);
}
