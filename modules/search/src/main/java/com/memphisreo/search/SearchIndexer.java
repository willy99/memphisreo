package com.memphisreo.search;

import java.util.UUID;

/**
 * Контракт, за яким PostgreSQL зараз можна замінити на Elasticsearch/
 * OpenSearch пізніше, без зміни викликів у ListingService. docs/architecture.md §8.
 */
public interface SearchIndexer {

    void index(ListingSearchDocument document);

    void remove(UUID listingId);
}
