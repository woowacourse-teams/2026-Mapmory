package com.mapmory.backend.member;

import com.mapmory.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소셜 제공자가 준 이름(없으면 기본 이름). 회원이 바꾸지 않는다.
    @Column(nullable = false, length = 50)
    private String name;

    // 다른 회원에게 보이는 이름. 제공자 이름과 관계없이 기본 이름으로 시작하고, 로그인으로 바뀌지 않는다.
    @Column(nullable = false, length = 20)
    private String nickname;

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

    protected Member() {
    }

    private Member(AuthProvider provider, String providerId, String name, UUID uuid) {
        this.provider = provider;
        this.providerId = providerId;
        this.name = name;
        this.nickname = MemberName.generated();
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
     */
    public void promote(AuthProvider provider, String providerId, String name) {
        this.provider = provider;
        this.providerId = providerId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNickname() {
        return nickname;
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
}
