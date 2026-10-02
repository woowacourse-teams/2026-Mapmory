CREATE TABLE place_rate_limit_bucket (
    id VARCHAR(100) NOT NULL PRIMARY KEY,
    state BLOB NULL,
    expires_at BIGINT NULL,
    INDEX idx_place_rate_limit_bucket_expires_at (expires_at)
) ENGINE = InnoDB;
