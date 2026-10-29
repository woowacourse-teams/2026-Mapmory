package com.mapmory.backend.notification.domain;

import com.mapmory.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * 회원이 고른 알림 설정. 한 번도 바꾸지 않은 회원은 행이 없고 {@link #defaultsFor}와 같다. 회원은 id로만 가리킨다.
 */
@Entity
@Table(name = "notification_setting")
public class NotificationSetting extends BaseEntity {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "group_album", nullable = false)
    private boolean groupAlbum;

    // 서비스 알림이라 처음엔 받는다. 광고성 알림이 생기면 그 종류는 동의 전까지 꺼짐이어야 한다.
    private static final boolean DEFAULT_GROUP_ALBUM = true;

    protected NotificationSetting() {
    }

    public NotificationSetting(Long memberId, boolean groupAlbum) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.groupAlbum = groupAlbum;
    }

    public static NotificationSetting defaultsFor(Long memberId) {
        return new NotificationSetting(memberId, DEFAULT_GROUP_ALBUM);
    }

    public Long getMemberId() {
        return memberId;
    }

    public boolean isGroupAlbum() {
        return groupAlbum;
    }
}
