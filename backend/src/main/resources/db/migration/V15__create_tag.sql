CREATE TABLE tag (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    member_id       BIGINT      NOT NULL,
    name            VARCHAR(30) NOT NULL,
    name_key        VARCHAR(30) NOT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tag_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT uk_tag_member_name_key UNIQUE (member_id, name_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
