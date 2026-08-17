-- Control plane: docs/domain-model.md §1, docs/security.md §2
-- PostGIS — розширення на рівні БД (не per-schema), потрібне для
-- geography-полів у tenant-схемах (docs/architecture.md §6).
CREATE SCHEMA IF NOT EXISTS control_plane;
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE control_plane.country (
    code                        varchar(2) PRIMARY KEY,
    name                        varchar(255) NOT NULL,
    default_currency            varchar(3) NOT NULL,
    default_locale              varchar(10) NOT NULL,
    region                      varchar(50) NOT NULL,
    required_property_fields    jsonb NOT NULL
);

CREATE TABLE control_plane.tenant (
    id                  uuid PRIMARY KEY,
    name                varchar(255) NOT NULL,
    slug                varchar(100) NOT NULL UNIQUE,
    country_code        varchar(2) NOT NULL REFERENCES control_plane.country (code),
    region              varchar(50) NOT NULL,
    schema_name         varchar(100) NOT NULL UNIQUE,
    status              varchar(20) NOT NULL DEFAULT 'TRIAL',
    subscription_plan   varchar(50),
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE control_plane.account_identity (
    id                          uuid PRIMARY KEY,
    email                       varchar(255) NOT NULL UNIQUE,
    password_hash               varchar(255) NOT NULL,
    tenant_id                   uuid NOT NULL REFERENCES control_plane.tenant (id),
    agent_id                    uuid NOT NULL,
    status                      varchar(20) NOT NULL DEFAULT 'ACTIVE',
    token_version                integer NOT NULL DEFAULT 0,
    two_factor_enabled           boolean NOT NULL DEFAULT false,
    two_factor_secret            varchar(255),
    two_factor_recovery_codes    text,
    created_at                   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_identity_tenant ON control_plane.account_identity (tenant_id);

-- Свідомо окрема таблиця від account_identity — docs/security.md §2, §6
CREATE TABLE control_plane.platform_staff (
    id              uuid PRIMARY KEY,
    email           varchar(255) NOT NULL UNIQUE,
    password_hash   varchar(255) NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',
    token_version   integer NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now()
);
