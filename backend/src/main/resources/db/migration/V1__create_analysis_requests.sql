CREATE TABLE analysis_requests (
    id BIGSERIAL PRIMARY KEY,
    language VARCHAR(32) NOT NULL,
    error_message TEXT NOT NULL,
    analysis_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
