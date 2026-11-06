-- ==============================================================================
-- CareerPilot - V10 Add Job Match Reports Table
-- ==============================================================================

CREATE TABLE job_match_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    job_title VARCHAR(255),
    company_name VARCHAR(255),
    job_description TEXT NOT NULL,
    match_percentage DOUBLE PRECISION NOT NULL,
    extracted_experience VARCHAR(100),
    matched_skills JSONB,
    missing_skills JSONB,
    fit_summary TEXT,
    profile_tweaks JSONB,
    interview_topics JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_job_match_reports_user_id ON job_match_reports(user_id);
CREATE INDEX idx_job_match_reports_profile_id ON job_match_reports(profile_id);
CREATE INDEX idx_job_match_reports_created_at ON job_match_reports(created_at DESC);
