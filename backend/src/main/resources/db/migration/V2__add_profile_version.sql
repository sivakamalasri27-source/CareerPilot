-- ==============================================================================
-- CareerPilot - V2 Add Optimistic Locking to Profiles
-- ==============================================================================
ALTER TABLE profiles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
