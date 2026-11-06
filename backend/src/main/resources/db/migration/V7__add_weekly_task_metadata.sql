-- V7: Add estimated_time and why_this_matters to weekly_tasks table
ALTER TABLE weekly_tasks ADD COLUMN estimated_time VARCHAR(50);
ALTER TABLE weekly_tasks ADD COLUMN why_this_matters TEXT;
