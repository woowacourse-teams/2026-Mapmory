ALTER TABLE share_video_job
    ADD COLUMN started_at DATETIME NULL,
    ADD COLUMN finished_at DATETIME NULL,
    ADD COLUMN failure_code VARCHAR(50) NULL;
