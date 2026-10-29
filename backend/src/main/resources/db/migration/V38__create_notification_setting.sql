-- 회원이 그룹 앨범 알림을 끌 수 있게 한다(ADR 0022 2026-10-29 보완). 행이 없으면 켜짐이다.
-- 기기가 아니라 회원 단위라 다른 기기로 로그인해도 따라간다. 기기 알림 권한(OS)은 서버가 알 수 없어 두지 않는다.
CREATE TABLE notification_setting (
    member_id   BIGINT   NOT NULL PRIMARY KEY,
    group_album BOOLEAN  NOT NULL DEFAULT TRUE COMMENT '그룹 앨범 알림을 받는지',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_setting_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
