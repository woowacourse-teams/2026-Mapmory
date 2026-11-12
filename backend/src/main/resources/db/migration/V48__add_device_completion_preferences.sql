ALTER TABLE push_token
    ADD COLUMN photo_record_completion_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN travel_video_completion_enabled BOOLEAN NOT NULL DEFAULT TRUE;
