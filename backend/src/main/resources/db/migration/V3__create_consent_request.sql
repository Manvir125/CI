-- SOLICITUDES DE CONSENTIMIENTO
CREATE TABLE consent_requests (
    id                  BIGSERIAL PRIMARY KEY,
    nhc                 VARCHAR(20)  NOT NULL,
    episode_id          VARCHAR(50)  NOT NULL,
    template_id         BIGINT       NOT NULL REFERENCES consent_templates(id),
    professional_id     BIGINT       NOT NULL REFERENCES users(id),
    channel             VARCHAR(10)  NOT NULL CHECK (channel IN ('REMOTE','ONSITE')),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    cancellation_reason TEXT,
    patient_email       VARCHAR(200),
    patient_phone       VARCHAR(20),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_requests_nhc        ON consent_requests(nhc);
CREATE INDEX idx_requests_status     ON consent_requests(status);
CREATE INDEX idx_requests_professional ON consent_requests(professional_id);
CREATE INDEX idx_requests_created    ON consent_requests(created_at DESC);

-- TOKENS DE FIRMA (actualizamos la tabla ya creada añadiendo la FK)
ALTER TABLE sign_tokens
    ADD CONSTRAINT fk_sign_tokens_request
    FOREIGN KEY (consent_request_id) REFERENCES consent_requests(id);

-- NOTIFICACIONES
CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    consent_request_id  BIGINT       NOT NULL REFERENCES consent_requests(id),
    type                VARCHAR(30)  NOT NULL,
    channel             VARCHAR(10)  NOT NULL,
    recipient           VARCHAR(200) NOT NULL,
    subject             VARCHAR(300),
    body                TEXT,
    sent_at             TIMESTAMP,
    success             BOOLEAN      NOT NULL DEFAULT FALSE,
    error_message       TEXT
);