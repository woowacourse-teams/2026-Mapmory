package com.mapmory.backend.notification.domain;

import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    // 처음 바꾸는 요청이 동시에 와도 기본 키 충돌 없이 하나로 합친다.
    @Modifying
    @Query(value = """
            INSERT INTO notification_setting (member_id, group_album)
            VALUES (:memberId, :groupAlbum)
            ON DUPLICATE KEY UPDATE group_album = VALUES(group_album)
            """, nativeQuery = true)
    void upsertGroupAlbum(@Param("memberId") Long memberId, @Param("groupAlbum") boolean groupAlbum);

    // 받을 멤버 중 그룹 앨범 알림을 끈 회원. 행이 없는 회원은 기본값(켜짐, NotificationSetting.defaultsFor)이라 나오지 않는다.
    @Query("SELECT s.memberId FROM NotificationSetting s WHERE s.memberId IN :memberIds AND s.groupAlbum = false")
    Set<Long> findGroupAlbumOffAmong(@Param("memberIds") Collection<Long> memberIds);
}
