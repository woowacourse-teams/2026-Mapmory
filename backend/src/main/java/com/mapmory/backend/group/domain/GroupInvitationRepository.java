package com.mapmory.backend.group.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    Optional<GroupInvitation> findByToken(String token);

    Optional<GroupInvitation> findByCode(String code);
}
