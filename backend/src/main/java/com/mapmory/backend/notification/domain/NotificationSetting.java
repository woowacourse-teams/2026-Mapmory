package com.mapmory.backend.notification.domain;

import com.mapmory.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * 회원이 고른 알림 설정. 행이 없으면 모두 켜짐이다. 회원은 id로만 가리킨다.
 */
@Entity
@Table(name = "notification_setting")
public class NotificationSetting extends BaseEntity {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "group_album", nullable = false)
    private boolean groupAlbum;

    protected NotificationSetting() {
    }

    public NotificationSetting(Long memberId, boolean groupAlbum) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.groupAlbum = groupAlbum;
    }

    public Long getMemberId() {
        return memberId;
    }

    public boolean isGroupAlbum() {
        return groupAlbum;
    }
}
