-- V9: Add profile content hash to audit_reports
ALTER TABLE audit_reports ADD COLUMN IF NOT EXISTS profile_content_hash VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_audit_reports_profile_hash ON audit_reports(profile_id, profile_content_hash);
