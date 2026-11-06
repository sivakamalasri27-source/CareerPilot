-- V11: Remove legacy failed/fallback audit reports (overall 50, all zero/empty section scores)
DELETE FROM audit_reports
WHERE overall_score = 50
  AND (
    CAST(section_scores AS VARCHAR) = '{}'
    OR CAST(section_scores AS VARCHAR) LIKE '%"headline": 0%'
    OR CAST(section_scores AS VARCHAR) LIKE '%"headline":0%'
    OR CAST(section_scores AS VARCHAR) = '{"headline":0,"about":0,"skills":0,"projects":0,"education":0}'
  );
