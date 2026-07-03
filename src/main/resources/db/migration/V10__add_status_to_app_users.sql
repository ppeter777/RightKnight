-- Adds a status column to the app_users table to store user status.
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- Backfill existing rows with ACTIVE status (for older databases with nulls).
UPDATE app_users SET status = 'ACTIVE' WHERE status IS NULL;