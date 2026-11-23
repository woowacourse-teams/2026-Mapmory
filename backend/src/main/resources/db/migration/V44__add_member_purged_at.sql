-- 탈퇴하거나 버려지고 30일이 지난 회원의 데이터를 지운 시각. NULL이면 아직 파기하지 않았다. (#435)
-- 회원 행은 남긴다. 파기 배치는 이 값이 비어 있는 회원을 다시 처리하므로, 실패해도 다음 날 이어서 지운다.
ALTER TABLE member
    ADD COLUMN purged_at DATETIME NULL COMMENT '회원 데이터를 파기한 시각';

-- 의견은 지우지 않고 작성자 연결만 끊는다(익명화).
ALTER TABLE service_feedback
    MODIFY COLUMN member_id BIGINT NULL;
