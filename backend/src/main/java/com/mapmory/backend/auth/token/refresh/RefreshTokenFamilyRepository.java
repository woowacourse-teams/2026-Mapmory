package com.mapmory.backend.auth.token.refresh;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamily, Long> {

    // 회전·로그아웃이 세션의 토큰을 잠그기 전에 세션 행을 잠근다(SELECT ... FOR UPDATE).
    // 같은 세션의 회전·재사용 판정·로그아웃이 직렬화되어, 세션 토큰을 서로 다른 순서로 잠그는 데드락이 없다. (#467, ADR 0028)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from RefreshTokenFamily f where f.id = :id")
    Optional<RefreshTokenFamily> findByIdForUpdate(@Param("id") Long id);
}
