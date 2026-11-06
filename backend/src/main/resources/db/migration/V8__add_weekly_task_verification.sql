-- V8: Add task verification columns to weekly_tasks
ALTER TABLE weekly_tasks ADD COLUMN verification_type VARCHAR(50) DEFAULT 'SELF_REPORTED';
ALTER TABLE weekly_tasks ADD COLUMN verification_hint VARCHAR(255);
ALTER TABLE weekly_tasks ADD COLUMN verification_status VARCHAR(50) DEFAULT 'UNVERIFIED';
ALTER TABLE weekly_tasks ADD COLUMN proof_url VARCHAR(500);
ALTER TABLE weekly_tasks ADD COLUMN baseline_count INT DEFAULT 0;
