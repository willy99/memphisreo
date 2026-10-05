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
- PostgreSQL 16 + PostGIS, Flyway; MinIO (S3) для файлів
- Web: React 19 + Vite + TypeScript, react-i18next (`web/`)
- Версії Java/Maven — `.sdkmanrc` (`sdk env`)

## Команди

- Інфраструктура: `docker compose up -d` (Postgres + MinIO)
- Збірка: `mvn -DskipTests package`
- Unit-тести: `mvn test`
- Інтеграційні тести (`*IT`, Testcontainers, потрібен Docker): `mvn verify`
- Один IT: `mvn -pl platform-app verify -Dit.test=InquiryLeadFlowIT`
- Запуск бекенду: `./build.sh` (http://localhost:8080)
- Фронт: `npm --prefix web run dev` (http://localhost:5173), лінт: `npm --prefix web run lint`

## Структура

- `modules/<context>` — bounded contexts (property, listing, crm, document, ...).
  Модуль не звертається до таблиць/сутностей іншого модуля: лише сирі UUID
  і публічний Java API. Жодних JPA-звʼязків між модулями.
- `platform-app` — композиційний корінь: REST-контролери (`api/`),
  крос-модульна оркестрація, конфігурація, міграції (`src/main/resources/db/migration`).

## Правила мультитенантності (критично)

- `tenant_id` береться ТІЛЬКИ з JWT, ніколи з path/query/body.
- Кожна tenant-scoped таблиця: колонка `tenant_id`, RLS-політика з `FORCE`,
  індекси починаються з `tenant_id`, FK — складені `(tenant_id, ...)`.
  Деталі — `docs/architecture.md` §3.
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
