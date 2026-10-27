-- 앱 Clock이 Asia/Seoul이라 LocalDateTime.now(clock)으로 만든 시각만 KST로 저장되었다.
-- 서버 JVM·DB·created_at은 UTC이므로 이 컬럼들을 9시간 앞당겨 UTC로 맞춘다. 이 배포부터 Clock은 UTC다. (#502)
-- 옛 프로세스가 멈춘 뒤 새 프로세스가 마이그레이션을 먼저 돌리므로, 이 시점의 값은 모두 KST로 쓰인 것이다.
-- updated_at에는 ON UPDATE CURRENT_TIMESTAMP가 걸려 있어, 보정이 수정 시각을 바꾸지 않도록 그대로 둔다.
-- NULL - INTERVAL은 NULL이라 값이 없는 컬럼은 그대로 남는다.

UPDATE member
SET promoted_at  = promoted_at - INTERVAL 9 HOUR,
    withdrawn_at = withdrawn_at - INTERVAL 9 HOUR,
    abandoned_at = abandoned_at - INTERVAL 9 HOUR,
    updated_at   = updated_at
WHERE promoted_at IS NOT NULL
   OR withdrawn_at IS NOT NULL
   OR abandoned_at IS NOT NULL;

UPDATE launch_waitlist
SET consented_at = consented_at - INTERVAL 9 HOUR,
    updated_at   = updated_at;

UPDATE group_member
SET joined_at  = joined_at - INTERVAL 9 HOUR,
    updated_at = updated_at;

UPDATE group_invitation
SET expires_at = expires_at - INTERVAL 9 HOUR,
    updated_at = updated_at;
