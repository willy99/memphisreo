-- Скидання пароля поштою — docs/security.md §10.
-- Control plane (без RLS): запит на скидання приходить до логіну, tenant ще невідомий.
-- У БД — лише SHA-256 від токена: витік таблиці не дає змінити чужий пароль.
CREATE TABLE control_plane.password_reset_token (
    id              uuid PRIMARY KEY,
    account_id      uuid NOT NULL REFERENCES control_plane.account_identity (id),
    token_hash      varchar(64) NOT NULL UNIQUE,
    expires_at      timestamptz NOT NULL,
    used_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_token_account ON control_plane.password_reset_token (account_id);
