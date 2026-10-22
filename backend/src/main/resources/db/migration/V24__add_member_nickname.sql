-- 닉네임: 다른 회원에게 보이는 이름. 회원이 직접 바꿀 수 있다.
-- name은 소셜 제공자가 준 이름(없으면 기본 이름)으로 남고 회원이 바꾸지 않는다.
ALTER TABLE member
    ADD COLUMN nickname VARCHAR(20) NULL COMMENT '다른 회원에게 보이는 이름' AFTER name;

-- 닉네임은 제공자 이름과 관계없이 기본 이름(회원 + 5자리 숫자)으로 시작한다. 기존 회원도 같다.
-- RAND()는 행마다 다시 계산된다.
UPDATE member
SET nickname = CONCAT('회원', FLOOR(10000 + RAND() * 90000));

ALTER TABLE member
    MODIFY COLUMN nickname VARCHAR(20) NOT NULL COMMENT '다른 회원에게 보이는 이름';
