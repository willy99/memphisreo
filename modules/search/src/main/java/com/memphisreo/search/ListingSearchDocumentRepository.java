package com.memphisreo.search;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ListingSearchDocumentRepository extends JpaRepository<ListingSearchDocument, UUID> {
}
