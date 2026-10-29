-- 앨범 알림을 앨범 쓰기와 같은 트랜잭션에 적어 두고 스케줄러가 꺼내 보낸다(ADR 0022 2026-10-29 보완: 아웃박스).
-- 배포·장애로 서버가 내려가도 PENDING 행은 남아 다시 보낸다. 실패하면 next_attempt_at을 미뤄 다시 시도하고, 5번째 실패면 FAILED다.
-- 집어 간 행은 next_attempt_at을 5분 뒤로 미뤄 둔다(처리 중 잠금). 보내다 서버가 죽으면 5분 뒤 다시 집힌다.
CREATE TABLE push_outbox (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(30)  NOT NULL COMMENT 'ALBUM_UPDATED',
    payload         JSON         NOT NULL COMMENT '보낼 때 다시 읽는 이벤트',
    status          VARCHAR(10)  NOT NULL COMMENT 'PENDING, SENT, FAILED',
    attempts        INT          NOT NULL DEFAULT 0 COMMENT '실패한 횟수',
    next_attempt_at DATETIME(6)  NOT NULL COMMENT '이 시각부터 보낼 차례다',
    last_error      VARCHAR(500) NULL COMMENT '마지막 실패 이유',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_push_outbox_due (status, next_attempt_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
