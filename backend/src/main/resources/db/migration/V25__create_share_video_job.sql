CREATE TABLE share_video_job (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    video_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    snapshot_json JSON NOT NULL,
    result_object_key VARCHAR(500),
    failure_reason VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_share_video_job_member
                             FOREIGN KEY (member_id) REFERENCES member(id)
);