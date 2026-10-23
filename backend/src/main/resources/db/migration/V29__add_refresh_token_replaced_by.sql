-- refresh 회전 체인. 이 토큰을 회전해 발급한 후속 토큰의 id를 기록한다.
--
-- 게스트가 회전 응답을 받지 못한 채 이전 토큰을 다시 보내면, 후속 토큰이 아직 쓰이지 않았는지 보고
-- 탈취와 구분한다. (ADR 0023)
--
-- 같은 테이블을 가리키지만 FK는 두지 않는다. 일괄 DELETE 때 행 순서에 따라 제약 위반이 날 수 있고,
-- 후속 토큰이 지워졌다면 "쓸 수 있는 후속 토큰이 없다"로 처리하면 되기 때문이다.
ALTER TABLE refresh_token
    ADD COLUMN replaced_by_id BIGINT NULL COMMENT '회전으로 발급한 후속 refresh 토큰 id';
