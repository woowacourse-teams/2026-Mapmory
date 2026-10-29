package com.mapmory.backend.notification.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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

    // 탈퇴하거나 버려진 회원의 기기 전부.
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.memberId = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);

    // FCM이 더 이상 유효하지 않다고 답한 토큰.
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.token IN :tokens")
    void deleteAllByTokenIn(@Param("tokens") Collection<String> tokens);
}
