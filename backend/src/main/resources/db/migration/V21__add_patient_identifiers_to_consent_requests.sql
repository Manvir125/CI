ALTER TABLE consent_requests
    ADD COLUMN IF NOT EXISTS patient_dni VARCHAR(32);

ALTER TABLE consent_requests
    ADD COLUMN IF NOT EXISTS patient_sip VARCHAR(64);
