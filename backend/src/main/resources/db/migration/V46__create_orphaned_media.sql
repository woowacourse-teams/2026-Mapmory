-- 기록에서 떨어져 나가 S3에서 지워야 할 객체. 기록 삭제·수정과 같은 트랜잭션에서 적고, 하루 한 번 배치가 지운다. (#162)
-- 지우고 나면 행을 없앤다. 실패하면 행이 남아 다음 날 다시 지운다.
CREATE TABLE orphaned_media (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    object_key VARCHAR(500) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_orphaned_media_object_key UNIQUE (object_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
