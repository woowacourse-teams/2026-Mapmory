-- 기존 소셜 계정으로 로그인하면서 두고 떠난 게스트를 표시한다. 행과 기록은 지우지 않고,
-- 정리는 GuestAbandoned 이벤트를 받는 컨텍스트가 파기 기간을 정해 한다. (ADR 0021)
ALTER TABLE member
    ADD COLUMN abandoned_at DATETIME NULL COMMENT '버려진 게스트가 된 시각 (기존 계정 로그인으로 접근 불가)';
