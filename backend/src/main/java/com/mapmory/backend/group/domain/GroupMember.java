package com.mapmory.backend.group.domain;

import com.mapmory.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * TravelGroup 애그리거트 안쪽 엔티티. 그룹 밖에서 직접 만들거나 저장하지 않는다.
 *
 * <p>joinedAt은 방장을 넘길 때 "가장 먼저 들어온 멤버"를 고르는 기준이다.
 */
@Entity
@Table(name = "group_member")
public class GroupMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private TravelGroup group;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    protected GroupMember() {
    }

    GroupMember(TravelGroup group, Long memberId, LocalDateTime joinedAt) {
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
    }

    boolean isMember(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
