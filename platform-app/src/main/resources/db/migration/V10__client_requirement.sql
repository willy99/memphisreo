-- Крок 2 воркфлоу: запит клієнта (що шукає) для підбору об'єктів — docs/sales-workflow.md §3.5.
-- Один активний запит на клієнта; історія запитів — не потрібна у Фазі 1.
CREATE TABLE app.client_requirement (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    client_id           uuid NOT NULL,
    property_type       varchar(20),
    rooms_min           integer,
    rooms_max           integer,
    price_min           numeric(14, 2),
    price_max           numeric(14, 2),
    currency            varchar(3) NOT NULL DEFAULT 'USD',
    area_min            numeric(10, 2),
    -- Райони / населені пункти текстом (як у адресі), порівняння без регістру.
    districts           jsonb NOT NULL DEFAULT '[]'::jsonb,
    market              varchar(20),
    -- Обов'язкові зручності: коди Property.Feature.
    must_have           jsonb NOT NULL DEFAULT '[]'::jsonb,
    notes               varchar(1000),
    active              boolean NOT NULL DEFAULT true,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, client_id),
    FOREIGN KEY (tenant_id, client_id) REFERENCES app.client (tenant_id, id) ON DELETE CASCADE
);

ALTER TABLE app.client_requirement ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.client_requirement FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.client_requirement
    USING (tenant_id = app.current_tenant_id()) WITH CHECK (tenant_id = app.current_tenant_id());
GRANT SELECT, INSERT, UPDATE, DELETE ON app.client_requirement TO memphisreo_app;

-- Контакт клієнта з якого об'єкта прийшов (заявка з сайту) — зв'язок interest.
ALTER TABLE app.client ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
