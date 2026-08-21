package com.mapmory.backend.tag;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
    long countByMemberId(Long memberId);

    boolean existsByMemberIdAndNameKey(Long memberId, String nameKey);

    boolean existsByMemberIdAndNameKeyAndIdNot(Long memberId, String nameKey, Long id);

    Optional<Tag> findByIdAndMemberId(Long id, Long memberId);

    List<Tag> findAllByMemberIdOrderByCreatedAtAscIdAsc(Long memberId);

    List<Tag> findAllByMemberIdAndIdIn(Long memberId, Collection<Long> ids);
}
