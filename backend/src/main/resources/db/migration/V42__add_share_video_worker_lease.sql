ALTER TABLE share_video_job
    ADD COLUMN claim_token CHAR(36) NULL,
    ADD COLUMN lease_expires_at DATETIME NULL,
    ADD COLUMN attempt_id CHAR(36) NULL,
    ADD COLUMN attempt_object_key VARCHAR(500) NULL,
    ADD INDEX idx_share_video_job_lease (status, lease_expires_at, id);
