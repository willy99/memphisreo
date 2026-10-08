-- Форма об'єкта і медіатека — docs/property-form.md.
-- Поля й переліки — з дослідження DIM.RIA XML spec / OpenImmo / RESO.

-- Чернетка зберігається з неповними даними; обов'язковість перевіряє
-- PropertyService при переведенні в ACTIVE, залежно від типу об'єкта.
ALTER TABLE app.property
    ALTER COLUMN area_sqm DROP NOT NULL,
    ADD COLUMN title              varchar(150),
    ADD COLUMN market             varchar(20),
    ADD COLUMN living_area_sqm    numeric(10, 2),
    ADD COLUMN kitchen_area_sqm   numeric(10, 2),
    ADD COLUMN ceiling_height_m   numeric(4, 2),
    ADD COLUMN wall_material      varchar(30),
    ADD COLUMN condition          varchar(30),
    ADD COLUMN heating            varchar(30),
    ADD COLUMN land_purpose       varchar(30),
    ADD COLUMN commercial_type    varchar(30),
    ADD COLUMN cadastral_number   varchar(30),
    -- Мультивибір зручностей/комунікацій: масив кодів (Java enum Property.Feature).
    ADD COLUMN features           jsonb NOT NULL DEFAULT '[]'::jsonb;

-- Ділянка може не мати вулиці/номера; місто стає обов'язковим лише при завершенні.
ALTER TABLE app.address
    ALTER COLUMN city DROP NOT NULL,
    ALTER COLUMN street DROP NOT NULL,
    ALTER COLUMN house_number DROP NOT NULL,
    ADD COLUMN complex_name  varchar(150),
    -- Як отримано координати: AUTOCOMPLETE / PIN / MANUAL.
    ADD COLUMN geocode_source varchar(20);

-- Кадастровий номер тепер типізоване поле об'єкта (для ділянки/будинку), а не
-- обов'язковий атрибут КОЖНОГО об'єкта в Україні (квартира його не має).
UPDATE control_plane.country
SET required_property_fields = '{"type": "object", "properties": {}, "required": []}'::jsonb
WHERE code = 'UA';

-- Медіатека: фото, планування, відео (файл) і відео-посилання.
-- Таблиця була порожньою заготовкою — перебудовуємо.
DROP TABLE app.property_media;

CREATE TABLE app.property_media (
    id                      uuid PRIMARY KEY,
    tenant_id               uuid NOT NULL,
    property_id             uuid NOT NULL,
    kind                    varchar(20) NOT NULL,
    position                integer NOT NULL DEFAULT 0,
    is_cover                boolean NOT NULL DEFAULT false,
    caption                 varchar(300),
    -- Фото/планування: перекодовані JPEG-варіанти (EXIF/GPS прибрано), оригінал не зберігається.
    large_key               varchar(500),
    thumb_key               varchar(500),
    -- Відео-файл: оригінал як є.
    original_key            varchar(500),
    external_url            varchar(500),
    original_filename       varchar(255),
    mime_type               varchar(100),
    size_bytes              bigint,
    width                   integer,
    height                  integer,
    uploaded_by_agent_id    uuid NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, property_id) REFERENCES app.property (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, uploaded_by_agent_id) REFERENCES app.agent (tenant_id, id)
);

CREATE INDEX idx_property_media_tenant_property ON app.property_media (tenant_id, property_id, kind, position);
-- Не більше однієї обкладинки на об'єкт.
CREATE UNIQUE INDEX idx_property_media_one_cover ON app.property_media (tenant_id, property_id) WHERE is_cover;

-- RLS — та сама політика, що й для решти таблиць схеми app (ADR-001).
ALTER TABLE app.property_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.property_media FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.property_media
    USING (tenant_id = app.current_tenant_id())
    WITH CHECK (tenant_id = app.current_tenant_id());
GRANT SELECT, INSERT, UPDATE, DELETE ON app.property_media TO memphisreo_app;

CREATE INDEX idx_listing_tenant_property_status ON app.listing (tenant_id, property_id, status);
