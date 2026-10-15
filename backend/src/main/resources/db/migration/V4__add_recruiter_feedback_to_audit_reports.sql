-- ==============================================================================
-- CareerPilot - V4 Add recruiter_feedback column to audit_reports
-- ==============================================================================
ALTER TABLE audit_reports ADD COLUMN recruiter_feedback JSONB;
