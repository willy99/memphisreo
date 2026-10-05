-- CRM: Client, Lead, LeadActivity — docs/domain-model.md §5
-- Застосовується як новим tenant-ам (TenantProvisioningService), так і
-- вже існуючим (TenantMaintenanceRunner) — docs/architecture.md §3.

CREATE TABLE client (
    id              uuid PRIMARY KEY,
    first_name      varchar(100) NOT NULL,
    last_name       varchar(100) NOT NULL,
    email           varchar(255) NOT NULL,
    phone           varchar(30),
    source          varchar(30) NOT NULL DEFAULT 'OTHER',
    notes           text,
    tenant_id       uuid NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_client_email ON client (email);

CREATE TABLE lead (
    id                      uuid PRIMARY KEY,
    client_id               uuid NOT NULL REFERENCES client (id),
    listing_id               uuid,
    assigned_agent_id        uuid NOT NULL,
    source_inquiry_id         uuid,
    status                  varchar(30) NOT NULL DEFAULT 'NEW',
    next_follow_up_at         timestamptz,
    tenant_id               uuid NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_lead_client ON lead (client_id);
CREATE INDEX idx_lead_status ON lead (status);
CREATE INDEX idx_lead_agent ON lead (assigned_agent_id);

CREATE TABLE lead_activity (
    id              uuid PRIMARY KEY,
    lead_id         uuid NOT NULL REFERENCES lead (id),
    agent_id        uuid NOT NULL,
    note            text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_lead_activity_lead ON lead_activity (lead_id);

-- Захист від подвійної конвертації одного inquiry в кілька лідів.
ALTER TABLE inquiry ADD COLUMN converted_to_lead_id uuid;
