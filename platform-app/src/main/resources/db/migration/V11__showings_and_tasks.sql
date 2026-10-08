-- Крок 3 воркфлоу: покази і задачі агента — docs/sales-workflow.md §3.

CREATE TABLE app.showing (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    property_id         uuid NOT NULL,
    agent_id            uuid NOT NULL,
    scheduled_at        timestamptz NOT NULL,
    duration_minutes    integer NOT NULL DEFAULT 45,
    status              varchar(20) NOT NULL DEFAULT 'SCHEDULED',
    notes               varchar(1000),
    cancel_reason       varchar(300),
    -- Фідбек після показу (§3.4)
    interest            integer,
    objection           varchar(20),
    feedback_comment    varchar(1000),
    next_step           varchar(20),
    feedback_at         timestamptz,
    created_by_agent_id uuid NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, property_id) REFERENCES app.property (tenant_id, id),
    FOREIGN KEY (tenant_id, agent_id) REFERENCES app.agent (tenant_id, id),
    FOREIGN KEY (tenant_id, created_by_agent_id) REFERENCES app.agent (tenant_id, id),
    CHECK (interest IS NULL OR interest BETWEEN 1 AND 5)
);

CREATE INDEX idx_showing_tenant_agent_time ON app.showing (tenant_id, agent_id, scheduled_at);
CREATE INDEX idx_showing_tenant_property_time ON app.showing (tenant_id, property_id, scheduled_at);

-- Груповий показ: кілька клієнтів на один слот.
CREATE TABLE app.showing_client (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    showing_id      uuid NOT NULL,
    client_id       uuid NOT NULL,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, showing_id, client_id),
    FOREIGN KEY (tenant_id, showing_id) REFERENCES app.showing (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, client_id) REFERENCES app.client (tenant_id, id)
);

CREATE INDEX idx_showing_client_tenant_client ON app.showing_client (tenant_id, client_id);

-- Задачі агента: показ — теж задача; після показу — задача на фідбек/власника.
CREATE TABLE app.task (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    kind                varchar(20) NOT NULL,
    title               varchar(300) NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'OPEN',
    due_at              timestamptz NOT NULL,
    assignee_agent_id   uuid NOT NULL,
    property_id         uuid,
    client_id           uuid,
    showing_id          uuid,
    note                varchar(1000),
    completed_at        timestamptz,
    created_by_agent_id uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, assignee_agent_id) REFERENCES app.agent (tenant_id, id),
    FOREIGN KEY (tenant_id, property_id) REFERENCES app.property (tenant_id, id),
    FOREIGN KEY (tenant_id, client_id) REFERENCES app.client (tenant_id, id),
    FOREIGN KEY (tenant_id, showing_id) REFERENCES app.showing (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, created_by_agent_id) REFERENCES app.agent (tenant_id, id)
);

CREATE INDEX idx_task_tenant_assignee_due ON app.task (tenant_id, assignee_agent_id, status, due_at);
CREATE INDEX idx_task_tenant_showing ON app.task (tenant_id, showing_id);

-- iCal-підписка агента: один токен на агента (у БД — хеш).
CREATE TABLE app.calendar_token (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    agent_id        uuid NOT NULL,
    token_hash      varchar(64) NOT NULL UNIQUE,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, agent_id),
    FOREIGN KEY (tenant_id, agent_id) REFERENCES app.agent (tenant_id, id)
);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['showing', 'showing_client', 'task', 'calendar_token'] LOOP
        EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON app.%I USING (tenant_id = app.current_tenant_id()) '
                       'WITH CHECK (tenant_id = app.current_tenant_id())', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON app.%I TO memphisreo_app', t);
    END LOOP;
END $$;
