-- Tenant plane: спільні таблиці всіх агенцій комірки — docs/adr/001-shared-schema-rls-cells.md.
-- Правила для КОЖНОЇ таблиці цієї схеми:
--   * колонка tenant_id NOT NULL (RLS-політики вмикає V5 і кожна наступна міграція);
--   * UNIQUE (tenant_id, id) — ціль складених FK; PK лишається id (Hibernate
--     шукає за id в UPDATE/DELETE);
--   * FK — складені (tenant_id, ...): БД не дасть послатись на запис іншої агенції;
--   * індекси починаються з tenant_id.
CREATE SCHEMA IF NOT EXISTS app;

CREATE TABLE app.agent (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    first_name          varchar(100) NOT NULL,
    last_name           varchar(100) NOT NULL,
    email               varchar(255) NOT NULL,
    phone               varchar(30),
    license_number      varchar(100),
    status              varchar(20) NOT NULL DEFAULT 'INVITED',
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);

CREATE TABLE app.address (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    country_code    varchar(2) NOT NULL,
    region          varchar(100),
    city            varchar(100) NOT NULL,
    district        varchar(100),
    street          varchar(255) NOT NULL,
    house_number    varchar(20) NOT NULL,
    postal_code     varchar(20),
    geo_location    geography(Point, 4326),
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);

CREATE INDEX idx_address_tenant_city ON app.address (tenant_id, city, district);
CREATE INDEX idx_address_geo ON app.address USING GIST (geo_location);

CREATE TABLE app.property (
    id                      uuid PRIMARY KEY,
    tenant_id               uuid NOT NULL,
    type                    varchar(20) NOT NULL,
    address_id              uuid NOT NULL,
    unit_number             varchar(20),
    area_sqm                numeric(10, 2) NOT NULL,
    land_area_sqm           numeric(10, 2),
    rooms                   integer,
    bedrooms                integer,
    bathrooms               integer,
    floor                   integer,
    total_floors            integer,
    year_built              integer,
    has_elevator            boolean,
    parking_spaces          integer,
    description             text,
    attributes              jsonb,
    status                  varchar(20) NOT NULL DEFAULT 'DRAFT',
    created_by_agent_id     uuid NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, address_id) REFERENCES app.address (tenant_id, id),
    FOREIGN KEY (tenant_id, created_by_agent_id) REFERENCES app.agent (tenant_id, id)
);

CREATE INDEX idx_property_tenant_status ON app.property (tenant_id, status, updated_at DESC);
CREATE INDEX idx_property_tenant_address ON app.property (tenant_id, address_id);
CREATE INDEX idx_property_attributes ON app.property USING GIN (attributes);

CREATE TABLE app.property_media (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    property_id     uuid NOT NULL,
    type            varchar(20) NOT NULL,
    url             varchar(1024) NOT NULL,
    order_index     integer NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, property_id) REFERENCES app.property (tenant_id, id)
);

CREATE INDEX idx_property_media_tenant_property ON app.property_media (tenant_id, property_id, order_index);

CREATE TABLE app.listing (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    property_id     uuid NOT NULL,
    agent_id        uuid NOT NULL,
    deal_type       varchar(20) NOT NULL,
    price           numeric(14, 2) NOT NULL,
    currency        varchar(3) NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'DRAFT',
    published_at    timestamptz,
    closed_at       timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, property_id) REFERENCES app.property (tenant_id, id),
    FOREIGN KEY (tenant_id, agent_id) REFERENCES app.agent (tenant_id, id)
);

CREATE INDEX idx_listing_tenant_status ON app.listing (tenant_id, status, updated_at DESC);
CREATE INDEX idx_listing_tenant_property ON app.listing (tenant_id, property_id);
CREATE INDEX idx_listing_tenant_agent ON app.listing (tenant_id, agent_id);

CREATE TABLE app.listing_price_history (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    listing_id      uuid NOT NULL,
    price           numeric(14, 2) NOT NULL,
    currency        varchar(3) NOT NULL,
    changed_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, listing_id) REFERENCES app.listing (tenant_id, id)
);

CREATE INDEX idx_listing_price_history_tenant_listing ON app.listing_price_history (tenant_id, listing_id, changed_at);

CREATE TABLE app.inquiry (
    id                      uuid PRIMARY KEY,
    tenant_id               uuid NOT NULL,
    listing_id              uuid NOT NULL,
    agent_id                uuid NOT NULL,
    contact_name            varchar(255) NOT NULL,
    contact_email           varchar(255) NOT NULL,
    contact_phone           varchar(30),
    message                 text,
    status                  varchar(20) NOT NULL DEFAULT 'NEW',
    -- Захист від подвійної конвертації в кілька лідів — docs/domain-model.md §5.
    converted_to_lead_id    uuid,
    created_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, listing_id) REFERENCES app.listing (tenant_id, id),
    FOREIGN KEY (tenant_id, agent_id) REFERENCES app.agent (tenant_id, id)
);

CREATE INDEX idx_inquiry_tenant_listing ON app.inquiry (tenant_id, listing_id);
CREATE INDEX idx_inquiry_tenant_status ON app.inquiry (tenant_id, status, created_at DESC);

-- RBAC — docs/security.md §3
CREATE TABLE app.role (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    name                varchar(100) NOT NULL,
    is_system_default   boolean NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);

CREATE TABLE app.role_permission (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    role_id             uuid NOT NULL,
    permission_code     varchar(100) NOT NULL,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, role_id) REFERENCES app.role (tenant_id, id)
);

CREATE INDEX idx_role_permission_tenant_role ON app.role_permission (tenant_id, role_id);

CREATE TABLE app.agent_role (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    agent_id    uuid NOT NULL,
    role_id     uuid NOT NULL,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, agent_id) REFERENCES app.agent (tenant_id, id),
    FOREIGN KEY (tenant_id, role_id) REFERENCES app.role (tenant_id, id)
);

CREATE INDEX idx_agent_role_tenant_agent ON app.agent_role (tenant_id, agent_id);

-- CRM — docs/domain-model.md §5
CREATE TABLE app.client (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    first_name      varchar(100) NOT NULL,
    last_name       varchar(100) NOT NULL,
    email           varchar(255) NOT NULL,
    phone           varchar(30),
    source          varchar(30) NOT NULL DEFAULT 'OTHER',
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);

CREATE INDEX idx_client_tenant_email ON app.client (tenant_id, email);

CREATE TABLE app.lead (
    id                      uuid PRIMARY KEY,
    tenant_id               uuid NOT NULL,
    client_id               uuid NOT NULL,
    listing_id              uuid,
    assigned_agent_id       uuid NOT NULL,
    source_inquiry_id       uuid,
    status                  varchar(30) NOT NULL DEFAULT 'NEW',
    next_follow_up_at       timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, client_id) REFERENCES app.client (tenant_id, id),
    FOREIGN KEY (tenant_id, listing_id) REFERENCES app.listing (tenant_id, id),
    FOREIGN KEY (tenant_id, assigned_agent_id) REFERENCES app.agent (tenant_id, id),
    FOREIGN KEY (tenant_id, source_inquiry_id) REFERENCES app.inquiry (tenant_id, id)
);

CREATE INDEX idx_lead_tenant_status ON app.lead (tenant_id, status, updated_at DESC);
CREATE INDEX idx_lead_tenant_agent ON app.lead (tenant_id, assigned_agent_id);
CREATE INDEX idx_lead_tenant_client ON app.lead (tenant_id, client_id);

ALTER TABLE app.inquiry
    ADD FOREIGN KEY (tenant_id, converted_to_lead_id) REFERENCES app.lead (tenant_id, id);

CREATE TABLE app.lead_activity (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    lead_id         uuid NOT NULL,
    agent_id        uuid NOT NULL,
    note            text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, lead_id) REFERENCES app.lead (tenant_id, id),
    FOREIGN KEY (tenant_id, agent_id) REFERENCES app.agent (tenant_id, id)
);

CREATE INDEX idx_lead_activity_tenant_lead ON app.lead_activity (tenant_id, lead_id, created_at DESC);

-- Документи — docs/domain-model.md §6.5
CREATE TABLE app.document (
    id                      uuid PRIMARY KEY,
    tenant_id               uuid NOT NULL,
    property_id             uuid NOT NULL,
    deal_id                 uuid,
    type                    varchar(30) NOT NULL,
    object_key              varchar(1024) NOT NULL,
    file_name               varchar(255) NOT NULL,
    content_type            varchar(100),
    file_size_bytes         bigint,
    valid_until             timestamptz,
    visibility              varchar(30) NOT NULL DEFAULT 'AGENCY_INTERNAL',
    parsing_status          varchar(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    extracted_data          jsonb,
    uploaded_by_agent_id    uuid NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, property_id) REFERENCES app.property (tenant_id, id),
    FOREIGN KEY (tenant_id, uploaded_by_agent_id) REFERENCES app.agent (tenant_id, id)
);

CREATE INDEX idx_document_tenant_property ON app.document (tenant_id, property_id);
CREATE INDEX idx_document_tenant_deal ON app.document (tenant_id, deal_id);
