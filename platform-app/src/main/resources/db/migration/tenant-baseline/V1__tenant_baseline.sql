-- Tenant-схема baseline. Виконується TenantProvisioningService (модуль
-- tenant) окремо на кожну схему при онбордингу агенції — не при старті
-- застосунку. Без schema-префіксів: Flyway задає search_path на потрібну
-- tenant-схему перед виконанням. docs/architecture.md §3, docs/domain-model.md §2-3.

CREATE TABLE agent (
    id                  uuid PRIMARY KEY,
    first_name          varchar(100) NOT NULL,
    last_name           varchar(100) NOT NULL,
    email               varchar(255) NOT NULL,
    phone               varchar(30),
    license_number      varchar(100),
    status              varchar(20) NOT NULL DEFAULT 'INVITED',
    tenant_id           uuid NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE address (
    id              uuid PRIMARY KEY,
    country_code    varchar(2) NOT NULL,
    region          varchar(100),
    city            varchar(100) NOT NULL,
    district        varchar(100),
    street          varchar(255) NOT NULL,
    house_number    varchar(20) NOT NULL,
    postal_code     varchar(20),
    geo_location    geography(Point, 4326),
    tenant_id       uuid NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_address_geo ON address USING GIST (geo_location);

CREATE TABLE property (
    id                      uuid PRIMARY KEY,
    type                    varchar(20) NOT NULL,
    address_id              uuid NOT NULL REFERENCES address (id),
    unit_number             varchar(20),
    area_sqm                numeric(10, 2) NOT NULL,
    rooms                   integer,
    floor                   integer,
    total_floors            integer,
    year_built              integer,
    attributes              jsonb,
    status                  varchar(20) NOT NULL DEFAULT 'DRAFT',
    created_by_agent_id     uuid NOT NULL REFERENCES agent (id),
    tenant_id               uuid NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_property_address ON property (address_id);
CREATE INDEX idx_property_status ON property (status);
CREATE INDEX idx_property_attributes ON property USING GIN (attributes);

CREATE TABLE property_media (
    id              uuid PRIMARY KEY,
    property_id     uuid NOT NULL REFERENCES property (id),
    type            varchar(20) NOT NULL,
    url             varchar(1024) NOT NULL,
    order_index     integer NOT NULL DEFAULT 0,
    tenant_id       uuid NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_property_media_property ON property_media (property_id);

CREATE TABLE listing (
    id              uuid PRIMARY KEY,
    property_id     uuid NOT NULL REFERENCES property (id),
    agent_id        uuid NOT NULL REFERENCES agent (id),
    deal_type       varchar(20) NOT NULL,
    price           numeric(14, 2) NOT NULL,
    currency        varchar(3) NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'DRAFT',
    tenant_id       uuid NOT NULL,
    published_at    timestamptz,
    closed_at       timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_listing_property ON listing (property_id);
CREATE INDEX idx_listing_status ON listing (status);

CREATE TABLE listing_price_history (
    id              uuid PRIMARY KEY,
    listing_id      uuid NOT NULL REFERENCES listing (id),
    price           numeric(14, 2) NOT NULL,
    currency        varchar(3) NOT NULL,
    changed_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_listing_price_history_listing ON listing_price_history (listing_id);

CREATE TABLE inquiry (
    id              uuid PRIMARY KEY,
    listing_id      uuid NOT NULL REFERENCES listing (id),
    agent_id        uuid NOT NULL REFERENCES agent (id),
    contact_name    varchar(255) NOT NULL,
    contact_email   varchar(255) NOT NULL,
    contact_phone   varchar(30),
    message         text,
    status          varchar(20) NOT NULL DEFAULT 'NEW',
    tenant_id       uuid NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_inquiry_listing ON inquiry (listing_id);

-- RBAC — docs/security.md §3
CREATE TABLE role (
    id                  uuid PRIMARY KEY,
    name                varchar(100) NOT NULL,
    is_system_default   boolean NOT NULL DEFAULT false,
    tenant_id           uuid NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE role_permission (
    id                  uuid PRIMARY KEY,
    role_id             uuid NOT NULL REFERENCES role (id),
    permission_code     varchar(100) NOT NULL
);

CREATE INDEX idx_role_permission_role ON role_permission (role_id);

CREATE TABLE agent_role (
    id          uuid PRIMARY KEY,
    agent_id    uuid NOT NULL REFERENCES agent (id),
    role_id     uuid NOT NULL REFERENCES role (id)
);

CREATE INDEX idx_agent_role_agent ON agent_role (agent_id);
