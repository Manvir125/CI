-- Añade campos de PDF a consent_requests
ALTER TABLE consent_requests
    ADD COLUMN pdf_path        TEXT,
    ADD COLUMN pdf_hash        VARCHAR(64),
    ADD COLUMN pdf_generated_at TIMESTAMP;

-- Añade token de kiosco para firma presencial
ALTER TABLE sign_tokens
    ADD COLUMN token_type VARCHAR(20) NOT NULL DEFAULT 'REMOTE';

CREATE INDEX idx_sign_tokens_type ON sign_tokens(token_type);