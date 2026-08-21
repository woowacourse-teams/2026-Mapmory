CREATE TABLE travel_record_tag (
    id               BIGINT   AUTO_INCREMENT PRIMARY KEY,
    travel_record_id BIGINT   NOT NULL,
    tag_id           BIGINT   NOT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_travel_record_tag_tag_record UNIQUE (tag_id, travel_record_id),
    CONSTRAINT fk_travel_record_tag_record
        FOREIGN KEY (travel_record_id) REFERENCES travel_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_record_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
