package com.mapmory.backend.group.domain;

import com.mapmory.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 그룹 초대. 링크용 토큰과 직접 입력용 6자리 코드를 함께 가진다.
 *
 * <p>그룹 하나에 초대가 여러 개 쌓이고 그룹과 수명이 달라 따로 둔 애그리거트다. 그룹은 id로만 가리킨다.
 * 토큰·코드 만들기, 만료 확인, 수락은 초대 태스크에서 채운다.
 */
@Entity
@Table(name = "group_invitation")
public class GroupInvitation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false, unique = true, length = 6, columnDefinition = "char(6)")
    private String code;

    @Column(name = "inviter_member_id", nullable = false)
    private Long inviterMemberId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected GroupInvitation() {
    }

    public GroupInvitation(Long groupId, String token, String code, Long inviterMemberId, LocalDateTime expiresAt) {
        this.groupId = Objects.requireNonNull(groupId, "groupId must not be null");
        this.token = Objects.requireNonNull(token, "token must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.inviterMemberId = Objects.requireNonNull(inviterMemberId, "inviterMemberId must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getToken() {
        return token;
    }

    public String getCode() {
        return code;
    }

    public Long getInviterMemberId() {
        return inviterMemberId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
