-- 사진 촬영일. 앱이 사진첩(EXIF/MediaStore)에서 읽어 보낸다. 모르면 NULL이다.
-- 서버에 없으면 다른 기기나 그룹 멤버의 앱에서 사진이 "날짜 미상"으로 묶인다.
-- 날짜만 저장한다. 앨범을 날짜로 묶는 데에는 시각이 필요 없다.
ALTER TABLE record_media
    ADD COLUMN captured_date DATE NULL AFTER sort_order;
