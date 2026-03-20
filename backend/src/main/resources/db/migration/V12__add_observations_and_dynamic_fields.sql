ALTER TABLE consent_requests ADD COLUMN observations TEXT;

CREATE TABLE consent_request_fields (
    consent_request_id BIGINT NOT NULL,
    field_key VARCHAR(255) NOT NULL,
    field_value TEXT,
    CONSTRAINT pk_consent_request_fields PRIMARY KEY (consent_request_id, field_key),
    CONSTRAINT fk_crf_consent_request FOREIGN KEY (consent_request_id) REFERENCES consent_requests (id) ON DELETE CASCADE
);
