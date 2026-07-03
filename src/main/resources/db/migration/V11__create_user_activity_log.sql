-- Creates the user_activity_log table for auditing user and admin actions.
CREATE TABLE IF NOT EXISTS user_activity_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    event_type VARCHAR(64) NOT NULL,
    method VARCHAR(16),
    path VARCHAR(512),
    status_code INTEGER,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes to speed up filtering by user, event type and recent entries
CREATE INDEX IF NOT EXISTS idx_user_activity_log_user_id ON user_activity_log(user_id);
CREATE INDEX IF NOT EXISTS idx_user_activity_log_event_type ON user_activity_log(event_type);
CREATE INDEX IF NOT EXISTS idx_user_activity_log_created_at ON user_activity_log(created_at DESC);