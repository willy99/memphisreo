-- Контакт може мати лише телефон (клієнт "з дзвінка") — docs/domain-model.md §5.
ALTER TABLE app.client ALTER COLUMN email DROP NOT NULL;
