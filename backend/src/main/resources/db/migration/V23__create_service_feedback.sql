CREATE TABLE service_feedback (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id  BIGINT       NOT NULL,
    category   VARCHAR(30)  NOT NULL,
    content    VARCHAR(2000) NOT NULL,
    platform   VARCHAR(20)  NOT NULL,
    status     VARCHAR(30)  NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_feedback_member FOREIGN KEY (member_id) REFERENCES member(id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE admin_audit_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    target_type VARCHAR(50) NOT NULL,
    target_id   BIGINT      NOT NULL,
    before_value VARCHAR(50),
    after_value  VARCHAR(50) NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
