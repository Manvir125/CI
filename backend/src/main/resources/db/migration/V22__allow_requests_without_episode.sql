ALTER TABLE consent_requests
    ALTER COLUMN episode_id DROP NOT NULL;

ALTER TABLE consent_groups
    ALTER COLUMN episode_id DROP NOT NULL;
