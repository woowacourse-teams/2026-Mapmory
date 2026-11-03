package com.mapmory.backend.auth.token.refresh;

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

    // 회전·로그아웃이 refresh를 잠그기 전에 회원과 세션부터 잠그려고 둘의 id만 읽는다. 잠그지 않는 조회다. (#579, #467)
    @Query("""
            select new com.mapmory.backend.auth.token.refresh.RefreshTokenOwner(r.member.id, r.family.id)
            from RefreshToken r where r.tokenHash = :tokenHash
            """)
    Optional<RefreshTokenOwner> findOwnerByTokenHash(@Param("tokenHash") String tokenHash);

    // 회전 검증 시 행 잠금(SELECT ... FOR UPDATE). 같은 토큰으로 동시 요청이 와도 직렬화되어
    // "한 refresh가 두 번 회전"되는 경쟁을 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.id = :id")
    Optional<RefreshToken> findByIdForUpdate(@Param("id") Long id);

    // 세션의 유효한 토큰을 모두 폐기한다. 로그아웃과 재사용 감지가 쓴다. 세션 행을 잠근 뒤에 부른다. (#467)
    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.family.id = :familyId and r.revokedAt is null")
    void revokeAllActiveByFamilyId(@Param("familyId") Long familyId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.member.id = :memberId and r.revokedAt is null")
    void revokeAllActiveByMemberId(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
