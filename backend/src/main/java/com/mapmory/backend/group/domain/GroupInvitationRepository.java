package com.mapmory.backend.group.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    Optional<GroupInvitation> findByToken(String token);

    Optional<GroupInvitation> findByCode(String code);

    // 그룹을 지울 때 초대도 지운다. 초대는 그룹과 다른 애그리거트라 cascade 대신 명시적으로 지운다(ADR 0017 cascade 규칙 1).
    @Modifying
    @Query("DELETE FROM GroupInvitation i WHERE i.groupId = :groupId")
    void deleteAllByGroupIdInBulk(@Param("groupId") Long groupId);
}
