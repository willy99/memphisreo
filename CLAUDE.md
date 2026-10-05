# Memphisreo

Мультитенантна SaaS-платформа для агенцій нерухомості (пілот — Україна,
далі — Європа). Продукт, архітектура й рішення — у `docs/`; читай
відповідний документ перед зміною домену:

- `docs/business-plan.md` — продукт, фази, скоуп
- `docs/architecture.md` — модулі, мультитенантність (ADR), пошук, storage
- `docs/domain-model.md` — сутності й життєві цикли
- `docs/security.md` — автентифікація, RBAC, ізоляція tenant-ів

Документація й коментарі в коді — українською.

## Стек

- Java 17, Spring Boot 3.3, Maven (multi-module), Lombok
- PostgreSQL 16 + PostGIS, Flyway; S3-сумісне сховище для файлів (локально — RustFS)
- Web: React 19 + Vite + TypeScript, react-i18next (`web/`)
- Версії Java/Maven — `.sdkmanrc` (`sdk env`)

## Команди

- **Усе одразу:** `./dev.sh` — Docker (Postgres + S3) → бекенд :8080 → фронт :5173,
  логи в `.dev-logs/`. `./dev.sh --reset` — з нуля (видаляє локальні дані),
  `--skip-build` — без перезбірки бекенду.
- Лише інфраструктура: `docker compose up -d`. Логін застосунку створюється
  init-скриптом лише на свіжому томі: після зміни `docker/postgres/init` —
  `./dev.sh --reset`.
- Збірка: `mvn -DskipTests clean package` (`clean` обов'язковий: старі
  міграції в `target/` ламають Flyway)
- Unit-тести: `mvn test`
- Інтеграційні тести (`*IT`, Testcontainers, потрібен Docker): `mvn verify`
- Один IT: `mvn -pl platform-app verify -Dit.test=InquiryLeadFlowIT`
- Фронт: `npm --prefix web run dev` (http://localhost:5173), лінт: `npm --prefix web run lint`

## Структура

- `modules/<context>` — bounded contexts (property, listing, crm, document, ...).
  Модуль не звертається до таблиць/сутностей іншого модуля: лише сирі UUID
  і публічний Java API. Жодних JPA-звʼязків між модулями.
- `platform-app` — композиційний корінь: REST-контролери (`api/`),
  крос-модульна оркестрація, конфігурація, міграції (`src/main/resources/db/migration`).

## Правила мультитенантності (критично)

- `tenant_id` береться ТІЛЬКИ з JWT, ніколи з path/query/body.
- Нова таблиця — у схемі `app`: `tenant_id NOT NULL`, `UNIQUE (tenant_id, id)`,
  FK — складені `(tenant_id, ...)`, індекси починаються з `tenant_id`, і в
  тій самій міграції — `ENABLE`/`FORCE ROW LEVEL SECURITY` + політика
  `tenant_isolation` (зразок — `V5__app_role_and_rls.sql`). Перевіряє
  `RowLevelSecurityInvariantIT`. Сутність — з `@TenantId` на `tenantId`.
- Застосунок ходить у БД логіном без прав власника (інакше RLS не діє);
  міграції — логіном власника. ADR — `docs/adr/001-shared-schema-rls-cells.md`.
- Крос-тенантний доступ (platform admin, пошуковий індекс) — лише через
  явно виділений шлях, ніколи "за замовчуванням".
- Нова сутність без тесту ізоляції (агенція A не бачить даних агенції B) — не готова.

## Правила коду

- Конструкторна інʼєкція, `final` поля; DTO — `record`.
- Сервіси не повертають `null` — `Optional<T>` або виняток.
- Доменні винятки — `NotFoundException`/`ForbiddenException` з `common`,
  мапляться в `ApiExceptionHandler`. Не ковтати винятки.
- Логування — SLF4J (`@Slf4j`), параметризовано; без `System.out`.
- Зміни стану сутностей — через методи сервісу, що перевіряють допустимий
  перехід; без тригерів у БД.
- Міграції — тільки нові файли `V<N>__...sql`, наявні не редагувати.
  Виняток: до першого прод-деплою baseline перезбирається один раз під
  перехід на shared-schema (ADR-001).
- Нова логіка сервісу — unit-тест (JUnit 5, AssertJ, Mockito); новий
  флоу через API — IT у `platform-app`.
