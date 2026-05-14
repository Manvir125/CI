CREATE TABLE user_favorite_templates (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    template_id BIGINT NOT NULL REFERENCES consent_templates(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_favorite_templates_template_id
    ON user_favorite_templates(template_id);
