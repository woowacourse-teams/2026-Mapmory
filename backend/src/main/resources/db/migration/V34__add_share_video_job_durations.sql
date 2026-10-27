ALTER TABLE share_video_job
    ADD COLUMN render_duration_ms BIGINT NULL,
    ADD COLUMN upload_duration_ms BIGINT NULL;
