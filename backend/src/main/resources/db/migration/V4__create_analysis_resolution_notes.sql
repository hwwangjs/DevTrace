CREATE TABLE analysis_resolution_notes (
    analysis_request_id BIGINT PRIMARY KEY,
    root_cause TEXT NOT NULL DEFAULT '',
    attempted_steps TEXT NOT NULL DEFAULT '',
    final_solution TEXT NOT NULL DEFAULT '',
    test_result TEXT NOT NULL DEFAULT '',
    resolution_status VARCHAR(16) NOT NULL DEFAULT 'UNRESOLVED'
        CHECK (resolution_status IN ('UNRESOLVED', 'IN_PROGRESS', 'RESOLVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_analysis_resolution_notes_request
        FOREIGN KEY (analysis_request_id)
        REFERENCES analysis_requests (id)
        ON DELETE CASCADE
);
