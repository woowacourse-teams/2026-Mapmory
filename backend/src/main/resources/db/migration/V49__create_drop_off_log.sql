-- 장소 검색·선택 한 번마다, 그리고 사진 업로드 주소 요청이 거절될 때마다 한 줄. 사용자가 어디서 막혀 기록을 남기지 못하고 나가는지 보고 재현하려고 30일만 둔다.
-- 상황(검색어, 고른 장소 ID, 앱 User-Agent) · 문제(outcome, error_code) · 결과(후보 수, 찾은 국가·지역)를 남긴다.
-- 같은 검색 세션은 session_token으로 잇고, 회원 ID와 장소 ID를 travel_record(member_id, place_id)와 맞춰 저장했는지 본다.
-- Google 약관상 장소는 place_id만 남기고 이름·주소·좌표는 남기지 않는다. 검색어는 사용자가 친 글자다.
-- 회원 FK는 걸지 않는다(파기 배치가 지운다).
CREATE TABLE drop_off_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT       NOT NULL,
    kind          VARCHAR(10)  NOT NULL,
    outcome       VARCHAR(30)  NOT NULL,
    session_token VARCHAR(36)  NULL,
    query         VARCHAR(100) NULL,
    result_count  INT          NULL,
    place_id      VARCHAR(255) NULL,
    country_code  VARCHAR(10)  NULL,
    region_code   VARCHAR(20)  NULL,
    file_count    INT          NULL,
    file_size     BIGINT       NULL,
    content_type  VARCHAR(100) NULL,
    error_code    VARCHAR(50)  NULL,
    user_agent    VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL,
    INDEX idx_drop_off_log_created_at (created_at),
    INDEX idx_drop_off_log_member (member_id, created_at),
    INDEX idx_drop_off_log_session (session_token)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
