-- 그룹원이 앨범에 올리면 다른 멤버에게 알림을 보내려고 기기 토큰을 둔다(ADR 0022 결정 7).
-- 한 회원이 기기를 여러 대 쓴다. 같은 토큰을 다른 회원이 등록하면(기기에서 계정을 바꿈) member_id를 새 회원으로 바꾼다.
CREATE TABLE push_token (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id  BIGINT       NOT NULL,
    token      VARCHAR(255) NOT NULL COMMENT 'FCM 등록 토큰',
    platform   VARCHAR(10)  NOT NULL COMMENT 'ANDROID, IOS',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_push_token_token UNIQUE (token),
    CONSTRAINT fk_push_token_member FOREIGN KEY (member_id) REFERENCES member (id),
    INDEX idx_push_token_member (member_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
