-- CAPTURAS DE FIRMA
CREATE TABLE signature_captures (
    id                  BIGSERIAL PRIMARY KEY,
    consent_request_id  BIGINT       NOT NULL REFERENCES consent_requests(id),
    signed_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    ip_address          VARCHAR(45),
    user_agent          TEXT,
    signature_image_path TEXT,
    sign_method         VARCHAR(20)  NOT NULL DEFAULT 'REMOTE_CANVAS',
    read_check_confirmed BOOLEAN     NOT NULL DEFAULT FALSE,
    patient_confirmation VARCHAR(20) NOT NULL DEFAULT 'SIGNED'
);

-- VERIFICACIONES DE IDENTIDAD DEL PACIENTE
CREATE TABLE patient_identity_verifications (
    id                  BIGSERIAL PRIMARY KEY,
    consent_request_id  BIGINT       NOT NULL REFERENCES consent_requests(id),
    attempted_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    ip_address          VARCHAR(45),
    attempt_number      INTEGER      NOT NULL DEFAULT 1,
    success             BOOLEAN      NOT NULL DEFAULT FALSE
);