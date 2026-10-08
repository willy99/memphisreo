package com.memphisreo.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ListingPriceHistoryRepository extends JpaRepository<ListingPriceHistory, UUID> {
}
