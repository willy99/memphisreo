-- Крос-tenant денормалізований пошуковий індекс — docs/architecture.md §8.
-- НЕ tenant-scoped: розв'язує колізію schema-per-tenant (ізоляція) vs
-- публічний пошук по всіх агенціях одразу.
CREATE SCHEMA IF NOT EXISTS search;

CREATE TABLE search.listing_search_document (
    listing_id      uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    property_id     uuid NOT NULL,
    deal_type       varchar(30) NOT NULL,
    property_type   varchar(30) NOT NULL,
    status          varchar(30) NOT NULL,
    country_code    varchar(2) NOT NULL,
    region          varchar(100),
    city            varchar(100),
    district        varchar(100),
    geo_location    geography(Point, 4326),
    price           numeric(14, 2) NOT NULL,
    currency        varchar(3) NOT NULL,
    area_sqm        numeric(10, 2),
    land_area_sqm   numeric(10, 2),
    rooms           integer,
    bedrooms        integer,
    bathrooms       integer,
    floor           integer,
    total_floors    integer,
    year_built      integer,
    description     text,
    published_at    timestamptz,
    updated_at      timestamptz NOT NULL DEFAULT now(),

    search_text tsvector GENERATED ALWAYS AS (
        to_tsvector('simple',
            coalesce(city, '') || ' ' || coalesce(district, '') || ' ' || coalesce(description, ''))
    ) STORED
);

-- Найчастіші фільтри публічного пошуку — country/deal_type/property_type/status разом.
CREATE INDEX idx_search_listing_filters
    ON search.listing_search_document (country_code, deal_type, property_type, status);
CREATE INDEX idx_search_listing_price ON search.listing_search_document (price);
CREATE INDEX idx_search_listing_bedrooms ON search.listing_search_document (bedrooms);
CREATE INDEX idx_search_listing_geo ON search.listing_search_document USING GIST (geo_location);
CREATE INDEX idx_search_listing_text ON search.listing_search_document USING GIN (search_text);
