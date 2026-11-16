CREATE TABLE background_jobs (
    id varchar(36) PRIMARY KEY,
    scope varchar(64) NOT NULL,
    type varchar(32) NOT NULL,
    payload text NOT NULL,
    status varchar(16) NOT NULL CHECK(status IN ('PENDING','RUNNING','DONE','FAILED')),
    attempts integer NOT NULL DEFAULT 0,
    available_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    lease_until timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    last_error varchar(120)
);
CREATE INDEX idx_background_jobs_pending ON background_jobs(scope,status,available_at,created_at);
CREATE INDEX idx_background_jobs_completed ON background_jobs(status,completed_at);
