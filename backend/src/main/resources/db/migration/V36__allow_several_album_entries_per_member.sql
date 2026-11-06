-- 한 앨범에 멤버가 기록을 여럿 담을 수 있게 한다. 같은 기록은 한 번만 담는다.
-- 지금 기록은 장소 하나라 여행 하나에 기록이 여럿 생긴다(ADR 0022 2026-10-28 보완).
--
-- 지금까지는 (album_id, member_id)가 유니크였고 기록 주인은 한 명이라, (album_id, travel_record_id)가 겹치는 행은 없다.
-- 새 유니크를 먼저 만든다. 옛 유니크가 fk_album_entry_album(album_id)의 인덱스를 겸해서,
-- 먼저 지우면 FK가 쓸 인덱스가 없어 MySQL이 거절한다.
ALTER TABLE album_entry
    ADD CONSTRAINT uk_album_entry_record UNIQUE (album_id, travel_record_id);

ALTER TABLE album_entry
    DROP INDEX uk_album_entry_member;
