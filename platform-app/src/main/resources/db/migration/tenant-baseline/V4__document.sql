-- Document: техпаспорт, правовстановлюючий документ, витяг з реєстру...
-- docs/domain-model.md §6.
CREATE TABLE document (
    id                      uuid PRIMARY KEY,
    property_id             uuid NOT NULL REFERENCES property (id),
    deal_id                 uuid,
    type                    varchar(30) NOT NULL,
    object_key              varchar(1024) NOT NULL,
    file_name               varchar(255) NOT NULL,
    content_type            varchar(100),
    file_size_bytes         bigint,
    valid_until             timestamptz,
    visibility              varchar(30) NOT NULL DEFAULT 'AGENCY_INTERNAL',
    parsing_status          varchar(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    extracted_data          jsonb,
    uploaded_by_agent_id    uuid NOT NULL REFERENCES agent (id),
    tenant_id               uuid NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_property ON document (property_id);
CREATE INDEX idx_document_deal ON document (deal_id);
