-- Region 계층은 parent_id로만 탐색한다.
-- V10 이관 당시 시·도에는 root_id만 설정되었으므로 부모 국가도 명시적으로 연결한다.
UPDATE region
SET parent_id = root_id
WHERE region_type = 'PROVINCE'
  AND parent_id IS NULL
  AND root_id IS NOT NULL;
