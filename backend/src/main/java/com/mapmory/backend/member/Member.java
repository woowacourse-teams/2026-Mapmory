package com.mapmory.backend.member;

import com.mapmory.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // name·nickname 컬럼. 두 이름이 언제 바뀌는지는 MemberName이 정한다.
    @Embedded
    private MemberName memberName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, unique = true, length = 36)
    private UUID uuid;

    // 소셜 로그인 정보. 기존 임시 회원(V11)은 값이 없을 수 있어 nullable 이며,
    // 신규 소셜 회원은 ofOAuth 로 항상 provider/providerId 를 채운다.
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id")
    private String providerId;

    // 탈퇴 시각. NULL이면 활동 중이다. (ADR 0021)
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    // 버려진 게스트가 된 시각. 게스트만 값이 있다. (ADR 0021)
    @Column(name = "abandoned_at")
    private LocalDateTime abandonedAt;

    // 게스트에서 소셜 회원으로 승격한 시각. 승격하면 provider가 바뀌어 처음부터 가입한 회원과 구분되지 않으므로 남긴다. (#440)
    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;

    protected Member() {
    }

    private Member(AuthProvider provider, String providerId, String name, UUID uuid) {
        this.provider = provider;
        this.providerId = providerId;
        this.memberName = MemberName.startingWith(name);
        this.uuid = uuid;
    }

    public static Member of(String name, UUID uuid) {
        return new Member(null, null, name, uuid);
    }

    public static Member ofOAuth(AuthProvider provider, String providerId, String name, UUID uuid) {
        return new Member(provider, providerId, name, uuid);
    }

    /**
     * 로그인하지 않은 사용자를 위한 회원. providerId는 서버가 발급한다.
     * 나중에 소셜 로그인을 하면 이 행의 provider/providerId만 교체해 승격한다. (ADR 0015)
     */
    public static Member ofGuest(String providerId, String name, UUID uuid) {
        return new Member(AuthProvider.GUEST, providerId, name, uuid);
    }

    /**
     * 게스트를 소셜 회원으로 승격한다.
     *
     * 새 회원을 만들지 않고 이 행의 소속만 바꾸므로, member_id를 참조하는 기록은 그대로 유지된다.
     * (ADR 0015)
     *
     * 이름은 제공자가 준 이름으로 바꾸고, 주지 않으면(애플 등) 게스트일 때의 이름을 그대로 쓴다.
     * 닉네임은 로그인으로 바뀌지 않는다.
     *
     * 활동 중인 게스트만 승격한다. 탈퇴한 게스트를 승격하면 지운 이름·providerId가 다시 채워져 되살아난다.
     * 호출부(MemberJoinService.findGuest)가 먼저 거르므로 여기서는 잘못된 호출을 막기만 한다.
     */
    public void promote(AuthProvider provider, String providerId, String providerName, LocalDateTime now) {
        if (this.provider != AuthProvider.GUEST || !isActive()) {
            throw new IllegalStateException("활동 중인 게스트만 승격할 수 있습니다. memberId=" + id);
        }
        this.provider = provider;
        this.providerId = providerId;
        this.memberName = memberName.promote(providerName);
        this.promotedAt = now;
    }

    /**
     * 닉네임을 바꾼다. 이름(name)은 그대로다.
     */
    public void changeNickname(String nickname) {
        this.memberName = memberName.changeNickname(nickname);
    }

    /**
     * 탈퇴한다. 이미 탈퇴했으면 아무것도 하지 않는다.
     *
     * 개인정보인 이름·닉네임·providerId를 지운다. providerId를 비워야 (provider, provider_id) UNIQUE가 풀려
     * 같은 소셜 계정으로 다시 가입할 수 있고, 그때는 새 회원이 된다.
     * provider는 어떤 제공자였는지 운영 통계에 남기려고, uuid는 다른 테이블과의 관계를 위해 그대로 둔다. (ADR 0021)
     */
    public void withdraw(LocalDateTime now) {
        if (isWithdrawn()) {
            return;
        }
        this.withdrawnAt = now;
        this.memberName = MemberName.withdrawn();
        this.providerId = null;
    }

    /**
     * 버려진 게스트로 표시한다. 이미 표시됐으면 아무것도 하지 않는다.
     *
     * 같은 사람이 이미 가입한 소셜 계정으로 로그인하면 기존 회원으로 들어가고, 게스트 회원에는 아무도 접근할 수 없게 된다.
     * 행과 기록은 지우지 않는다. 게스트의 이름·providerId는 서버가 만든 값이라 개인정보가 아니다. (ADR 0021)
     */
    public void abandon(LocalDateTime now) {
        if (this.provider != AuthProvider.GUEST) {
            throw new IllegalStateException("게스트만 버려진 게스트가 될 수 있습니다. memberId=" + id);
        }
        if (isAbandoned()) {
            return;
        }
        this.abandonedAt = now;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    public boolean isAbandoned() {
        return abandonedAt != null;
    }

    /**
     * 탈퇴하지도, 버려지지도 않은 회원이다.
     */
    public boolean isActive() {
        return !isWithdrawn() && !isAbandoned();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return memberName.name();
    }

    public String getNickname() {
        return memberName.nickname();
    }

    public UUID getUuid() {
        return uuid;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public LocalDateTime getWithdrawnAt() {
        return withdrawnAt;
    }

    public LocalDateTime getPromotedAt() {
        return promotedAt;
    }

    public LocalDateTime getAbandonedAt() {
        return abandonedAt;
    }
}
