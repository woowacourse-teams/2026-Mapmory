ALTER TABLE travel_record
    ADD COLUMN place_provider VARCHAR(20) NULL,
    ADD COLUMN place_id VARCHAR(255) NULL,
    ADD COLUMN place_name VARCHAR(500) NULL,
    ADD COLUMN place_attribution VARCHAR(255) NULL,
    ADD COLUMN place_attribution_url VARCHAR(500) NULL;
