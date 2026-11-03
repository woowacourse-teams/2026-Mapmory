-- refresh 토큰 세션 묶음(family). 로그인 한 번에서 회전으로 이어진 토큰을 한 세션으로 다룬다. (#467)
--
-- 로그아웃과 재사용 감지가 체인(replaced_by_id)을 한 행씩 따라가지 않고 세션 단위로 폐기한다.
-- 회전·로그아웃은 세션 행을 먼저 잠가, 같은 세션의 작업을 직렬화한다. (ADR 0028)
CREATE TABLE refresh_token_family (
    id         BIGINT   AUTO_INCREMENT PRIMARY KEY,
    member_id  BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_family_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE refresh_token
    ADD COLUMN family_id BIGINT NULL COMMENT '이 토큰이 속한 세션(refresh_token_family) id';

-- 기존 토큰을 세션으로 묶는다.
--
-- 각 토큰은 후속을 하나만 가리키므로(replaced_by_id), 체인을 끝까지 따라가면 끝 토큰이 하나로 정해진다.
-- 끝 토큰이 같은 토큰끼리 한 세션이다. 시작 토큰으로 묶지 않는 이유는 게스트 응답 유실 재발급(ADR 0023)이
-- 이전 토큰도 새 후속을 가리키게 해, 한 세션에 시작점이 둘 이상 생길 수 있기 때문이다.
-- 후속이 없거나 가리키는 행이 없는 토큰을 끝으로 본다(replaced_by_id에는 FK가 없다).
CREATE TABLE refresh_token_family_backfill (
    token_id    BIGINT NOT NULL PRIMARY KEY,
    terminal_id BIGINT NOT NULL
);

-- 재귀 깊이는 체인 길이다. 게스트는 오래 쓰면 회전이 수천 번 쌓일 수 있어 기본값(1000)을 넘긴다.
SET SESSION cte_max_recursion_depth = 1000000;

-- 재귀 한 단계마다 replaced_by_id로 이전 토큰을 찾는다. 인덱스가 없으면 단계마다 테이블 전체를 훑는다.
CREATE INDEX idx_refresh_replaced_by_backfill ON refresh_token (replaced_by_id);

INSERT INTO refresh_token_family_backfill (token_id, terminal_id)
WITH RECURSIVE chain (token_id, terminal_id) AS (
    SELECT t.id, t.id
    FROM refresh_token t
    WHERE t.replaced_by_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM refresh_token s WHERE s.id = t.replaced_by_id)
    UNION ALL
    SELECT r.id, c.terminal_id
    FROM refresh_token r
             JOIN chain c ON r.replaced_by_id = c.token_id
    WHERE r.id <> c.token_id
)
SELECT token_id, terminal_id
FROM chain;

-- 세션 id는 끝 토큰의 id를 그대로 쓴다. 이후 새 세션은 AUTO_INCREMENT가 가장 큰 id 다음부터 매긴다.
INSERT INTO refresh_token_family (id, member_id, created_at)
SELECT b.terminal_id, terminal.member_id, MIN(t.created_at)
FROM refresh_token_family_backfill b
         JOIN refresh_token t ON t.id = b.token_id
         JOIN refresh_token terminal ON terminal.id = b.terminal_id
GROUP BY b.terminal_id, terminal.member_id;

UPDATE refresh_token t
    JOIN refresh_token_family_backfill b ON b.token_id = t.id
SET t.family_id = b.terminal_id;

DROP TABLE refresh_token_family_backfill;
DROP INDEX idx_refresh_replaced_by_backfill ON refresh_token;
SET SESSION cte_max_recursion_depth = DEFAULT;

ALTER TABLE refresh_token
    MODIFY COLUMN family_id BIGINT NOT NULL COMMENT '이 토큰이 속한 세션(refresh_token_family) id',
    ADD CONSTRAINT fk_refresh_token_family FOREIGN KEY (family_id) REFERENCES refresh_token_family (id);

CREATE INDEX idx_refresh_family ON refresh_token (family_id);
