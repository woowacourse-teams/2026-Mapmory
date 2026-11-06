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
 * refresh 토큰 세션 묶음. 로그인 한 번에서 회전으로 이어진 토큰을 한 세션으로 묶는다. (#467)
 *
 * 로그아웃과 재사용 감지는 이 세션의 토큰을 한 번에 폐기한다. 회전·로그아웃은 이 행을 먼저 잠가
 * 같은 세션의 작업을 직렬화한다. 잠그는 순서는 회원 → 세션 → refresh 토큰이다. (ADR 0028)
 */
@Entity
@Table(name = "refresh_token_family")
public class RefreshTokenFamily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RefreshTokenFamily() {
    }

    private RefreshTokenFamily(Member member) {
        this.member = member;
    }

    /**
     * 로그인할 때 새 세션을 연다.
     */
    public static RefreshTokenFamily open(Member member) {
        return new RefreshTokenFamily(member);
    }

    public Long getId() {
        return id;
    }
}
