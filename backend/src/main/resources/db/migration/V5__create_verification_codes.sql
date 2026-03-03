CREATE TABLE verification_codes (
    id                  BIGSERIAL PRIMARY KEY,
    consent_request_id  BIGINT      NOT NULL REFERENCES consent_requests(id),
    code                VARCHAR(6)  NOT NULL,
    phone               VARCHAR(20) NOT NULL,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP   NOT NULL,
    used_at             TIMESTAMP,
    is_valid            BOOLEAN     NOT NULL DEFAULT TRUE,
    attempt_count       INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_verification_codes_request
    ON verification_codes(consent_request_id);
