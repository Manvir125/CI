-- Grupo de consentimientos para un episodio
CREATE TABLE consent_groups (
    id                  BIGSERIAL PRIMARY KEY,
    episode_id          VARCHAR(50)  NOT NULL,
    nhc                 VARCHAR(20)  NOT NULL,
    created_by_id       BIGINT       NOT NULL REFERENCES users(id),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    patient_email       VARCHAR(255),
    patient_phone       VARCHAR(20),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Relación entre grupo y solicitudes individuales
ALTER TABLE consent_requests
    ADD COLUMN group_id          BIGINT REFERENCES consent_groups(id),
    ADD COLUMN responsible_service VARCHAR(50),
    ADD COLUMN professional_signed    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN professional_signed_at TIMESTAMP,
    ADD COLUMN professional_signer_id BIGINT REFERENCES users(id);

ALTER TABLE users
    ADD COLUMN service_code VARCHAR(50);

CREATE INDEX idx_consent_requests_group    ON consent_requests(group_id);
CREATE INDEX idx_consent_requests_service  ON consent_requests(responsible_service);
CREATE INDEX idx_consent_groups_episode    ON consent_groups(episode_id);