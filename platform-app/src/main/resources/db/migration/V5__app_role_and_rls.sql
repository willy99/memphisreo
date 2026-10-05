-- Ролі БД і Row-Level Security — docs/adr/001-shared-schema-rls-cells.md (рубіж 2).
--
-- memphisreo_app — група, якою працює застосунок: не власник таблиць, без
-- BYPASSRLS, тож RLS діє на кожен запит. Логін-користувач (з паролем)
-- створюється інфраструктурою й включається в цю групу
-- (локально — docker/postgres/init/01-app-login.sql). Міграції виконує
-- власник таблиць (окремий логін), не застосунок.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'memphisreo_app') THEN
        CREATE ROLE memphisreo_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA control_plane, app, search TO memphisreo_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA control_plane, app, search TO memphisreo_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA control_plane, app, search
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO memphisreo_app;
REVOKE ALL ON control_plane.flyway_schema_history FROM memphisreo_app;

-- Tenant поточної транзакції, виставлений застосунком через
-- set_config('app.tenant_id', ..., true). NULLIF: після завершення
-- транзакції current_setting повертає '' (не NULL), а ''::uuid — помилка.
-- Не виставлено → NULL → політика не пропускає жодного рядка (fail closed).
CREATE FUNCTION app.current_tenant_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid $$;

-- Політика на кожну таблицю схеми app з колонкою tenant_id. Нові таблиці
-- в наступних міграціях мусять отримати те саме — перевіряє
-- RowLevelSecurityInvariantIT.
DO $$
DECLARE
    t record;
BEGIN
    FOR t IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
        WHERE n.nspname = 'app' AND c.relkind = 'r'
    LOOP
        EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY', t.relname);
        EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY', t.relname);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON app.%I '
            'USING (tenant_id = app.current_tenant_id()) '
            'WITH CHECK (tenant_id = app.current_tenant_id())',
            t.relname);
    END LOOP;
END
$$;
