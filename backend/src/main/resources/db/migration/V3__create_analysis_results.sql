CREATE TABLE analysis_results (
    analysis_request_id BIGINT PRIMARY KEY,
    result_json JSONB NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_analysis_results_request
        FOREIGN KEY (analysis_request_id)
        REFERENCES analysis_requests (id)
        ON DELETE CASCADE
);
