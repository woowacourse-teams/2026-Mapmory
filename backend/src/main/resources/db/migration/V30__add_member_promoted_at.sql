-- 게스트가 소셜 회원으로 승격한 시각. 승격은 provider를 덮어써 처음부터 소셜로 가입한 회원과 구분되지 않으므로 남긴다.
-- 승격 수와 승격까지 걸린 시간(promoted_at - created_at)을 세는 기준값이다. (#440)
ALTER TABLE member
    ADD COLUMN promoted_at DATETIME NULL COMMENT '게스트에서 소셜 회원으로 승격한 시각. 승격하지 않았으면 NULL';
