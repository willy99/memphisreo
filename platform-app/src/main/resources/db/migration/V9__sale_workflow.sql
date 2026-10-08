-- Крок 1 воркфлоу продажу — docs/sales-workflow.md §2, §5.

-- 1. Лістинг = вкладка "Продаж": новий життєвий цикл, власник, мандат, доступ.
UPDATE app.listing SET status = CASE status
    WHEN 'PUBLISHED' THEN 'ACTIVE'
    WHEN 'RESERVED'  THEN 'UNDER_OFFER'
    WHEN 'CLOSED'    THEN 'SOLD'
    WHEN 'ARCHIVED'  THEN 'WITHDRAWN'
    ELSE status END;

ALTER TABLE app.listing
    -- Чернетка продажу створюється разом з об'єктом — ціна може бути ще не відома.
    ALTER COLUMN price DROP NOT NULL,
    ADD COLUMN seller_client_id      uuid,
    ADD COLUMN mandate_type          varchar(20),
    ADD COLUMN mandate_valid_until   date,
    ADD COLUMN commission_percent    numeric(5, 2),
    ADD COLUMN commission_fixed      numeric(14, 2),
    -- Як потрапити на об'єкт: ключі в офісі / домовлятися з власником / код замка.
    ADD COLUMN access_notes          varchar(500),
    -- На публічній сторінці — лише район і приблизне коло замість точної адреси.
    ADD COLUMN hide_exact_address    boolean NOT NULL DEFAULT false,
    ADD COLUMN withdrawn_reason      varchar(300),
    ADD FOREIGN KEY (tenant_id, seller_client_id) REFERENCES app.client (tenant_id, id);

CREATE INDEX idx_listing_tenant_seller ON app.listing (tenant_id, seller_client_id);

-- Заявка з публічної сторінки: достатньо телефону АБО email.
ALTER TABLE app.inquiry ALTER COLUMN contact_email DROP NOT NULL;

-- 2. Кілька відповідальних агентів на об'єкт (рішення 2026-10-09).
CREATE TABLE app.property_agent (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    property_id     uuid NOT NULL,
    agent_id        uuid NOT NULL,
    -- LEAD — головний відповідальний (показується на публічній сторінці першим).
    role            varchar(20) NOT NULL DEFAULT 'CO_AGENT',
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, property_id, agent_id),
    FOREIGN KEY (tenant_id, property_id) REFERENCES app.property (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, agent_id) REFERENCES app.agent (tenant_id, id)
);

CREATE INDEX idx_property_agent_tenant_agent ON app.property_agent (tenant_id, agent_id);

INSERT INTO app.property_agent (id, tenant_id, property_id, agent_id, role)
SELECT gen_random_uuid(), tenant_id, id, created_by_agent_id, 'LEAD' FROM app.property;

-- 3. Таймлайн: append-only події по об'єкту/клієнту — основа історії та звіту власнику.
CREATE TABLE app.activity_event (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    subject_type        varchar(20) NOT NULL,
    subject_id          uuid NOT NULL,
    type                varchar(40) NOT NULL,
    payload             jsonb NOT NULL DEFAULT '{}'::jsonb,
    -- null = системна подія або публічний відвідувач.
    actor_agent_id      uuid,
    -- Чи показувати подію власнику у звіті (внутрішні нотатки — ні).
    owner_visible       boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, actor_agent_id) REFERENCES app.agent (tenant_id, id)
);

CREATE INDEX idx_activity_event_subject ON app.activity_event (tenant_id, subject_type, subject_id, created_at DESC);

-- 4. Доступ власника до звіту по своїх об'єктах — посилання без пароля.
CREATE TABLE app.owner_access_token (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    client_id       uuid NOT NULL,
    token_hash      varchar(64) NOT NULL UNIQUE,
    expires_at      timestamptz NOT NULL,
    created_by_agent_id uuid NOT NULL,
    last_used_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, client_id) REFERENCES app.client (tenant_id, id),
    FOREIGN KEY (tenant_id, created_by_agent_id) REFERENCES app.agent (tenant_id, id)
);

CREATE INDEX idx_owner_access_token_client ON app.owner_access_token (tenant_id, client_id);

-- RLS для нових таблиць (ADR-001).
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['property_agent', 'activity_event', 'owner_access_token'] LOOP
        EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON app.%I USING (tenant_id = app.current_tenant_id()) '
                       'WITH CHECK (tenant_id = app.current_tenant_id())', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON app.%I TO memphisreo_app', t);
    END LOOP;
END $$;

-- 5. Публічні контакти агенції для публічної сторінки.
ALTER TABLE control_plane.tenant
    ADD COLUMN public_phone     varchar(30),
    ADD COLUMN public_email     varchar(255),
    ADD COLUMN website          varchar(255),
    ADD COLUMN about            text,
    ADD COLUMN public_city      varchar(100);
