CREATE TABLE signature_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    signature_capture_id BIGINT REFERENCES signature_captures(id) ON DELETE CASCADE,
    sequence_order INT NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    pressure DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) NOT NULL,
    max_x DOUBLE PRECISION,
    max_y DOUBLE PRECISION,
    max_pressure DOUBLE PRECISION
);

CREATE INDEX idx_sig_events_user ON signature_events(user_id);
CREATE INDEX idx_sig_events_capture ON signature_events(signature_capture_id);
