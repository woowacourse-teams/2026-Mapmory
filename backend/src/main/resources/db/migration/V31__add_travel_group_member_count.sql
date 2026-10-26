-- 그룹 인원 수. 초대 수락이 그룹 행을 잠그고 이 값으로 30명 제한을 판단한다.
-- 멤버 목록(group_member)은 잠금과 상관없는 일반 SELECT라 동시에 수락하면 옛 스냅샷을 읽지만,
-- 잠금으로 읽은 그룹 행은 항상 최신이다. 멤버가 들어오고 나갈 때 TravelGroup이 같이 바꾼다.
ALTER TABLE travel_group
    ADD COLUMN member_count INT NOT NULL DEFAULT 1 COMMENT '멤버 수. 최대 30' AFTER owner_member_id;

UPDATE travel_group g
SET g.member_count = (SELECT COUNT(*) FROM group_member gm WHERE gm.group_id = g.id);
