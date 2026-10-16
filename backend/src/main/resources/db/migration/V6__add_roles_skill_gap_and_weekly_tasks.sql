-- ==============================================================================
-- CareerPilot - V6 Add Target Roles, Skill Gap Reports, and Weekly Tasks Updates
-- ==============================================================================

-- 1. Curated Target Roles Catalog
CREATE TABLE target_roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Role Required Skills
CREATE TABLE role_required_skills (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES target_roles(id) ON DELETE CASCADE,
    skill_name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    weight INTEGER NOT NULL DEFAULT 1,
    priority VARCHAR(20) NOT NULL DEFAULT 'IMPORTANT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_role_skill UNIQUE (role_id, skill_name)
);

CREATE INDEX idx_role_required_skills_role_id ON role_required_skills(role_id);

-- Seed Curated Target Roles
INSERT INTO target_roles (name, description) VALUES
('Java Backend Developer', 'Core enterprise backend development with Java, Spring Boot, relational databases, containerization, and REST APIs.'),
('Full Stack Java Developer', 'Full-cycle web application engineering using Java/Spring Boot for APIs and modern frontend frameworks (React/TypeScript).'),
('Java + AI Engineer', 'Backend engineering combining solid Java enterprise architectures with LLM orchestration, RAG pipelines, and vector databases.');

-- Seed Skills for Java Backend Developer
INSERT INTO role_required_skills (role_id, skill_name, category, weight, priority)
SELECT id, 'java', 'Core Language', 3, 'CORE' FROM target_roles WHERE name = 'Java Backend Developer' UNION ALL
SELECT id, 'spring boot', 'Framework', 3, 'CORE' FROM target_roles WHERE name = 'Java Backend Developer' UNION ALL
SELECT id, 'postgresql', 'Database', 3, 'CORE' FROM target_roles WHERE name = 'Java Backend Developer' UNION ALL
SELECT id, 'rest apis', 'Architecture', 2, 'IMPORTANT' FROM target_roles WHERE name = 'Java Backend Developer' UNION ALL
SELECT id, 'docker', 'DevOps', 2, 'IMPORTANT' FROM target_roles WHERE name = 'Java Backend Developer' UNION ALL
SELECT id, 'git', 'Tooling', 2, 'IMPORTANT' FROM target_roles WHERE name = 'Java Backend Developer' UNION ALL
SELECT id, 'junit', 'Testing', 2, 'IMPORTANT' FROM target_roles WHERE name = 'Java Backend Developer' UNION ALL
SELECT id, 'microservices', 'Architecture', 1, 'NICE_TO_HAVE' FROM target_roles WHERE name = 'Java Backend Developer' UNION ALL
SELECT id, 'redis', 'Database', 1, 'NICE_TO_HAVE' FROM target_roles WHERE name = 'Java Backend Developer';

-- Seed Skills for Full Stack Java Developer
INSERT INTO role_required_skills (role_id, skill_name, category, weight, priority)
SELECT id, 'java', 'Core Language', 3, 'CORE' FROM target_roles WHERE name = 'Full Stack Java Developer' UNION ALL
SELECT id, 'spring boot', 'Framework', 3, 'CORE' FROM target_roles WHERE name = 'Full Stack Java Developer' UNION ALL
SELECT id, 'javascript', 'Frontend', 3, 'CORE' FROM target_roles WHERE name = 'Full Stack Java Developer' UNION ALL
SELECT id, 'react', 'Frontend', 3, 'CORE' FROM target_roles WHERE name = 'Full Stack Java Developer' UNION ALL
SELECT id, 'postgresql', 'Database', 2, 'IMPORTANT' FROM target_roles WHERE name = 'Full Stack Java Developer' UNION ALL
SELECT id, 'rest apis', 'Architecture', 2, 'IMPORTANT' FROM target_roles WHERE name = 'Full Stack Java Developer' UNION ALL
SELECT id, 'git', 'Tooling', 2, 'IMPORTANT' FROM target_roles WHERE name = 'Full Stack Java Developer' UNION ALL
SELECT id, 'docker', 'DevOps', 1, 'NICE_TO_HAVE' FROM target_roles WHERE name = 'Full Stack Java Developer' UNION ALL
SELECT id, 'html/css', 'Frontend', 1, 'NICE_TO_HAVE' FROM target_roles WHERE name = 'Full Stack Java Developer';

-- Seed Skills for Java + AI Engineer
INSERT INTO role_required_skills (role_id, skill_name, category, weight, priority)
SELECT id, 'java', 'Core Language', 3, 'CORE' FROM target_roles WHERE name = 'Java + AI Engineer' UNION ALL
SELECT id, 'spring boot', 'Framework', 3, 'CORE' FROM target_roles WHERE name = 'Java + AI Engineer' UNION ALL
SELECT id, 'python', 'Core Language', 3, 'CORE' FROM target_roles WHERE name = 'Java + AI Engineer' UNION ALL
SELECT id, 'prompt engineering', 'AI / LLM', 3, 'CORE' FROM target_roles WHERE name = 'Java + AI Engineer' UNION ALL
SELECT id, 'rag', 'AI / LLM', 2, 'IMPORTANT' FROM target_roles WHERE name = 'Java + AI Engineer' UNION ALL
SELECT id, 'vector databases', 'Database', 2, 'IMPORTANT' FROM target_roles WHERE name = 'Java + AI Engineer' UNION ALL
SELECT id, 'postgresql', 'Database', 2, 'IMPORTANT' FROM target_roles WHERE name = 'Java + AI Engineer' UNION ALL
SELECT id, 'docker', 'DevOps', 2, 'IMPORTANT' FROM target_roles WHERE name = 'Java + AI Engineer' UNION ALL
SELECT id, 'git', 'Tooling', 1, 'NICE_TO_HAVE' FROM target_roles WHERE name = 'Java + AI Engineer';

-- 3. Skill Gap Reports Table
CREATE TABLE skill_gap_reports (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES target_roles(id) ON DELETE CASCADE,
    match_percentage DOUBLE PRECISION NOT NULL,
    matched_skills JSONB,
    missing_skills JSONB,
    weak_skills JSONB,
    learning_roadmap JSONB,
    summary TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skill_gap_reports_profile_id ON skill_gap_reports(profile_id);
CREATE INDEX idx_skill_gap_reports_created_at ON skill_gap_reports(created_at DESC);

-- 4. Update weekly_tasks table with user_id, week_start, and status
ALTER TABLE weekly_tasks ADD COLUMN user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE weekly_tasks ADD COLUMN week_start DATE;
ALTER TABLE weekly_tasks ADD COLUMN status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE weekly_tasks ALTER COLUMN profile_id DROP NOT NULL;

-- Populate user_id from profile_id
UPDATE weekly_tasks wt
SET user_id = p.user_id
FROM profiles p
WHERE wt.profile_id = p.id AND wt.user_id IS NULL;

-- Populate status from is_completed
UPDATE weekly_tasks SET status = 'DONE' WHERE is_completed = TRUE;
UPDATE weekly_tasks SET status = 'PENDING' WHERE is_completed = FALSE;

CREATE INDEX idx_weekly_tasks_user_week ON weekly_tasks(user_id, week_start);
