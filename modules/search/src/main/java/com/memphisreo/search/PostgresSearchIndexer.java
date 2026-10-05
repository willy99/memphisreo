package com.memphisreo.search;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostgresSearchIndexer implements SearchIndexer {

    private final ListingSearchDocumentRepository repository;

    public PostgresSearchIndexer(ListingSearchDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void index(ListingSearchDocument document) {
        repository.save(document);
    }

    @Override
    public void remove(UUID listingId) {
        repository.deleteById(listingId);
    }
}
