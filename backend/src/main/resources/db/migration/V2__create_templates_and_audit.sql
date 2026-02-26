-- PLANTILLAS DE CONSENTIMIENTO
CREATE TABLE consent_templates (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(300) NOT NULL,
    service_code   VARCHAR(50),
    procedure_code VARCHAR(50),
    content_html   TEXT         NOT NULL,
    version        INTEGER      NOT NULL DEFAULT 1,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by     BIGINT       REFERENCES users(id),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_templates_service  ON consent_templates(service_code);
CREATE INDEX idx_templates_active   ON consent_templates(is_active);

-- CAMPOS DINÁMICOS DE PLANTILLA
CREATE TABLE template_fields (
    id            BIGSERIAL PRIMARY KEY,
    template_id   BIGINT      NOT NULL REFERENCES consent_templates(id) ON DELETE CASCADE,
    field_key     VARCHAR(100) NOT NULL,
    field_label   VARCHAR(200) NOT NULL,
    field_type    VARCHAR(50)  NOT NULL,
    required      BOOLEAN      NOT NULL DEFAULT TRUE,
    default_value VARCHAR(500)
);

-- LOG DE AUDITORÍA (append-only, nunca se modifica)
CREATE TABLE audit_log (
    id             BIGSERIAL PRIMARY KEY,
    timestamp_utc  TIMESTAMP    NOT NULL DEFAULT NOW(),
    actor_id       VARCHAR(200),          -- user ID o 'patient_token:xxx'
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(100),
    entity_id      BIGINT,
    ip_address     VARCHAR(45),
    success        BOOLEAN      NOT NULL DEFAULT TRUE,
    detail_json    JSONB
);

CREATE INDEX idx_audit_timestamp  ON audit_log(timestamp_utc);
CREATE INDEX idx_audit_actor      ON audit_log(actor_id);
CREATE INDEX idx_audit_action     ON audit_log(action);

-- TOKENS DE FIRMA (los usaremos en el sprint 2, pero los creamos ya)
CREATE TABLE sign_tokens (
    id                  BIGSERIAL PRIMARY KEY,
    consent_request_id  BIGINT       NOT NULL,
    token_hash          VARCHAR(512) NOT NULL UNIQUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP    NOT NULL,
    used_at             TIMESTAMP,
    is_valid            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by_ip       VARCHAR(45)
);
