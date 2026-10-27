package com.mapmory.backend.group.domain;

import com.mapmory.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 그룹 초대. 링크용 토큰과 직접 입력용 6자리 코드를 함께 가진다.
 *
 * <p>그룹 하나에 초대가 여러 개 쌓이고 그룹과 수명이 달라 따로 둔 애그리거트다. 그룹은 id로만 가리킨다.
 * 발급 뒤 1일이 지나면 만료된다. 만료된 초대는 지우지 않고 찾지 못한 것으로 다룬다.
 */
@Entity
@Table(name = "group_invitation")
public class GroupInvitation extends BaseEntity {

    private static final Duration VALID_FOR = Duration.ofDays(1);

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

    /**
     * 멤버 누구나 초대를 만든다. 멤버인지는 부르는 쪽이 먼저 확인한다.
     */
    public static GroupInvitation issue(Long groupId, Long inviterMemberId, String token, String code,
                                        LocalDateTime issuedAt) {
        return new GroupInvitation(groupId, token, code, inviterMemberId, issuedAt.plus(VALID_FOR));
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
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
