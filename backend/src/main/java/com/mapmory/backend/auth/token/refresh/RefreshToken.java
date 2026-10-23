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

    public void revoke(LocalDateTime now) {
        this.revokedAt = now;
    }

    /**
     * 회전한다. 이미 폐기된 토큰이면 후속만 바꾼다(응답 유실 재발급 시 새 후속을 가리키게 한다).
     */
    public void replaceWith(RefreshToken successor, LocalDateTime now) {
        if (!isRevoked()) {
            this.revokedAt = now;
        }
        this.replacedById = successor.id;
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
