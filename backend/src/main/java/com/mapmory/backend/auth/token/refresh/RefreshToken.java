package com.mapmory.backend.auth.token.refresh;

import com.mapmory.backend.member.Member;
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
import org.hibernate.annotations.CreationTimestamp;

/**
 * refresh 토큰.
 *
 * 원문은 클라이언트에만 두고, 서버는 해시(tokenHash)만 저장한다.
 * 회전(rotation) 시 기존 토큰을 revoke하고 새 토큰을 발급하며, 로그아웃 시에도 revoke한다.
 * 회전으로 폐기된 토큰은 후속 토큰의 id(replacedById)를 남긴다. (ADR 0023)
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RefreshToken() {
    }

    private RefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(Member member, String tokenHash, LocalDateTime expiresAt) {
        return new RefreshToken(member, tokenHash, expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /**
     * 폐기한다. 이미 폐기됐으면 처음 폐기된 시각을 그대로 둔다.
     */
    public void revoke(LocalDateTime now) {
        if (isRevoked()) {
            return;
        }
        this.revokedAt = now;
    }

    /**
     * 회전한다. 폐기하고 후속 토큰을 가리킨다.
     */
    public void replaceWith(RefreshToken successor, LocalDateTime now) {
        this.revokedAt = now;
        this.replacedById = successor.id;
    }

    /**
     * other와 같은 후속 토큰을 가리킨다.
     *
     * 응답 유실로 재발급할 때, 이전 토큰도 새 후속을 가리키게 해 유실이 반복돼도 이전 토큰으로 이어지게 한다. (ADR 0023)
     */
    public void followSuccessorOf(RefreshToken other) {
        this.replacedById = other.replacedById;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public Long getReplacedById() {
        return replacedById;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public Member getMember() {
        return member;
    }
}
