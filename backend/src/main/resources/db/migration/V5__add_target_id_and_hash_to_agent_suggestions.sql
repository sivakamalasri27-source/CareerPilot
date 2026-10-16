-- Add target_id and original_text_hash to agent_suggestions for entity linking and stale detection
ALTER TABLE agent_suggestions ADD COLUMN target_id BIGINT;
ALTER TABLE agent_suggestions ADD COLUMN original_text_hash VARCHAR(64);

CREATE INDEX idx_agent_suggestions_target ON agent_suggestions(target_section, target_id);
