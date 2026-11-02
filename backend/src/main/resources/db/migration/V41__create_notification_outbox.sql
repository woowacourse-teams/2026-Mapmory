CREATE TABLE notification_outbox (
                                     id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,

    -- 알림 유형과 발생한 사건을 식별한다.
                                     event_type VARCHAR(40) NOT NULL,
                                     event_key VARCHAR(160)
                                            CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,

    -- 발송에 필요한 데이터. FCM 토큰 원문은 넣지 않는다.
                                     payload JSON NOT NULL,

    -- 한 행은 한 기기에 보낼 알림이다.
                                     member_id BIGINT NOT NULL,
                                     push_token_id BIGINT NOT NULL,

    -- 발송 상태와 재시도 정보
                                     status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                     attempt_count INT NOT NULL DEFAULT 0,
                                     next_attempt_at DATETIME NOT NULL,

    -- 워커 선점과 중단 복구에 사용한다.
                                     claim_token CHAR(36),
                                     lease_expires_at DATETIME,

    -- 최근 오류와 FCM 접수 시각
                                     last_error_code VARCHAR(100),
                                     sent_at DATETIME,

                                     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP,

    -- 같은 사건을 같은 등록 기기에 중복 저장하지 않는다.
                                     CONSTRAINT uk_notification_outbox_event_recipient
                                         UNIQUE (event_key, push_token_id),

    -- 발송 대기·재시도 이벤트 조회
                                     INDEX idx_notification_outbox_pending
                                         (status, next_attempt_at, id),

    -- 선점 기한이 지난 이벤트 조회
                                     INDEX idx_notification_outbox_lease
                                         (status, lease_expires_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;