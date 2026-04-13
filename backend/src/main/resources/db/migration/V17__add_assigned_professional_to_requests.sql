ALTER TABLE consent_requests
    ADD COLUMN assigned_professional_id BIGINT REFERENCES users(id);

CREATE INDEX idx_consent_requests_assigned_professional
    ON consent_requests(assigned_professional_id);
