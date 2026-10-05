-- ЛИШЕ для локальної розробки й тестів: логін, яким ходить застосунок.
-- Права видають міграції групі memphisreo_app (V5__app_role_and_rls.sql);
-- тут — тільки облікові дані, які в проді створює інфраструктура.
-- Не суперкористувач і без BYPASSRLS — інакше RLS не діятиме (ADR-001).
CREATE ROLE memphisreo_app NOLOGIN;
CREATE ROLE memphisreo_app_user LOGIN PASSWORD 'memphisreo_app' IN ROLE memphisreo_app;
