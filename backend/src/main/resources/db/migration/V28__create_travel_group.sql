-- 그룹 공유: 저장은 각자 기록에, 보기는 앨범 하나로.
-- 앨범은 여행 기록을 id로만 가리킨다. travel_record에는 FK를 걸지 않는다.
-- 기록이 지워지면 여행기록이 이벤트를 보내고 그룹이 album_entry를 지운다.

-- group은 MySQL 예약어라 travel_group으로 둔다.
CREATE TABLE travel_group (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(30) NOT NULL,
    owner_member_id BIGINT      NOT NULL COMMENT '방장. 나가면 가장 먼저 들어온 멤버에게 넘긴다',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_travel_group_owner FOREIGN KEY (owner_member_id) REFERENCES member(id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE group_member (
    id         BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    group_id   BIGINT   NOT NULL,
    member_id  BIGINT   NOT NULL,
    joined_at  DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_group_member UNIQUE (group_id, member_id),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES travel_group(id),
    CONSTRAINT fk_group_member_member FOREIGN KEY (member_id) REFERENCES member(id),
    INDEX idx_group_member_member (member_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE group_invitation (
    id                BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    group_id          BIGINT      NOT NULL,
    token             VARCHAR(64) NOT NULL COMMENT '초대 링크에 들어가는 값',
    code              CHAR(6)     NOT NULL COMMENT '직접 입력하는 6자리 코드',
    inviter_member_id BIGINT      NOT NULL,
    expires_at        DATETIME    NOT NULL,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_group_invitation_token UNIQUE (token),
    CONSTRAINT uk_group_invitation_code UNIQUE (code),
    CONSTRAINT fk_group_invitation_group FOREIGN KEY (group_id) REFERENCES travel_group(id),
    CONSTRAINT fk_group_invitation_inviter FOREIGN KEY (inviter_member_id) REFERENCES member(id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 앨범 이름·지역은 앨범이 갖는다. 기간은 담긴 기록들의 기간을 합쳐 계산하므로 칸이 없다.
-- 나중에 개인 기록에도 앨범을 쓸 수 있어서 group_album이 아니라 album으로 둔다.
CREATE TABLE album (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    group_id         BIGINT       NOT NULL,
    name             VARCHAR(200) NOT NULL,
    region_id        BIGINT       NOT NULL COMMENT '앨범 카드와 붙이기 기본값에 쓴다. 지도를 칠하지 않는다',
    opener_member_id BIGINT       NOT NULL COMMENT '앨범을 연 사람. 그 기록이 빠지면 가장 먼저 붙인 사람에게 넘긴다',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_album_group FOREIGN KEY (group_id) REFERENCES travel_group(id),
    CONSTRAINT fk_album_region FOREIGN KEY (region_id) REFERENCES region(id),
    CONSTRAINT fk_album_opener FOREIGN KEY (opener_member_id) REFERENCES member(id),
    INDEX idx_album_group (group_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 한 기록을 여러 앨범에 담을 수 있다. 한 앨범에는 멤버당 기록 하나다.
CREATE TABLE album_entry (
    id               BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    album_id         BIGINT   NOT NULL,
    travel_record_id BIGINT   NOT NULL COMMENT 'travel_record.id. 컨텍스트가 달라 FK를 걸지 않는다',
    member_id        BIGINT   NOT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_album_entry_member UNIQUE (album_id, member_id),
    CONSTRAINT fk_album_entry_album FOREIGN KEY (album_id) REFERENCES album(id),
    CONSTRAINT fk_album_entry_member FOREIGN KEY (member_id) REFERENCES member(id),
    INDEX idx_album_entry_travel_record (travel_record_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
