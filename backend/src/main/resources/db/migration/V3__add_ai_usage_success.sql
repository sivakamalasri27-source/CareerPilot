-- ==============================================================================
-- CareerPilot - V3 Add is_success column to ai_usage_logs
-- ==============================================================================
ALTER TABLE ai_usage_logs ADD COLUMN is_success BOOLEAN NOT NULL DEFAULT TRUE;
