package com.mapmory.backend.auth.refresh;

import com.mapmory.backend.member.Member;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 회전 검증 시 행 잠금(SELECT ... FOR UPDATE). 같은 토큰으로 동시 요청이 와도 직렬화되어
    // "한 refresh가 두 번 회전"되는 경쟁을 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.member = :member and r.revokedAt is null")
    void revokeAllActiveByMember(@Param("member") Member member, @Param("now") LocalDateTime now);
}
