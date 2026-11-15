ALTER TABLE plan_options
    ADD COLUMN estimate_source varchar(32),
    ADD COLUMN estimate_status varchar(32) NOT NULL DEFAULT 'NOT_APPLICABLE',
    ADD COLUMN estimate_updated_at timestamp(6) with time zone;

-- Preserve previous measurements without attributing them to an unknown provider.
UPDATE plan_options
SET estimate_status = CASE
        WHEN avg_distance_km IS NOT NULL AND avg_travel_minutes IS NOT NULL THEN 'READY'
        ELSE 'UNAVAILABLE'
    END,
    estimate_source = CASE
        WHEN avg_distance_km IS NOT NULL AND avg_travel_minutes IS NOT NULL THEN 'UNKNOWN'
        ELSE NULL
    END
WHERE type = 'PLACE';

CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC, id DESC);
