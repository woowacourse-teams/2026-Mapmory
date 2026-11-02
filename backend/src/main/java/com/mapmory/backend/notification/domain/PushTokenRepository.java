package com.mapmory.backend.notification.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    Optional<PushToken> findByToken(String token);

    // 알림 받을 멤버들의 기기. idx_push_token_member를 탄다.
    List<PushToken> findAllByMemberIdIn(Collection<Long> memberIds);

    // 로그아웃. 남의 토큰은 지우지 않는다.
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.token = :token AND t.memberId = :memberId")
    void deleteByTokenAndMemberId(@Param("token") String token, @Param("memberId") Long memberId);

    // FCM이 더 이상 유효하지 않다고 답한 토큰.
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.token IN :tokens")
    void deleteAllByTokenIn(@Param("tokens") Collection<String> tokens);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PushToken p WHERE p.token = :token")
    Optional<PushToken> findByTokenForUpdate(@Param("token") String token);

    @Modifying
    @Query("""
        DELETE FROM PushToken p
        WHERE p.id = :id AND p.memberId = :memberId
    """)
    void deleteByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);

    // 영상 요청 시 수신 대상 소유권 확인에 사용할 메서드
    Optional<PushToken> findByIdAndMemberId(Long id, Long memberId);
}
