-- 탈퇴는 회원 행을 지우지 않는다(soft). 다른 테이블이 member를 외래 키로 참조하고,
-- 각 컨텍스트가 MemberWithdrawn 이벤트를 받아 자기 데이터를 지운 뒤 행 삭제는 나중 작업으로 한다. (ADR 0021)
ALTER TABLE member
    ADD COLUMN withdrawn_at DATETIME NULL COMMENT '탈퇴 시각. NULL이면 활동 중';
