-- refresh 토큰
--
-- 원문은 클라이언트에만 보관하고, 서버는 SHA-256 해시(token_hash, 64 hex)만 저장한다.
-- 회전(rotation) 시 기존 토큰의 revoked_at 을 기록하고 새 토큰을 발급한다.
--
-- token_hash 는 조회 키이자 유니크. member_id 로 특정 회원의 토큰을 일괄 폐기한다.
CREATE TABLE refresh_token (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    member_id  BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at DATETIME     NOT NULL,
    revoked_at DATETIME     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_member    FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_refresh_member ON refresh_token (member_id);
