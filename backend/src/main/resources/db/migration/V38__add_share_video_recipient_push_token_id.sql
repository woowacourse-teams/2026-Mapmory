-- 요청 당시의 알림 수신 등록 ID를 보존한다.
-- NULL이면 알림 없이 영상만 생성한다.
ALTER TABLE share_video_job
    ADD COLUMN recipient_push_token_id BIGINT NULL;

-- push_token에 대한 FK는 추가하지 않는다.
-- 로그아웃으로 푸시 등록이 삭제되어도 영상 작업은 유지되어야 한다.
