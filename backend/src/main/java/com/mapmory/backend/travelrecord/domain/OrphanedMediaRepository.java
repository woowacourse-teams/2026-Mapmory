package com.mapmory.backend.travelrecord.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface OrphanedMediaRepository extends JpaRepository<OrphanedMedia, Long> {

    // 기록 삭제·수정과 같은 트랜잭션에서 적는다. 롤백되면 같이 사라져야 하므로 트랜잭션 없이는 부르지 못한다.
    // 큐에 있는 키는 다시 붙이지 못하지만(existsByObjectKeyIn), 그 확인을 지난 드문 경우에 같은 키가 또 올 수 있어 무시한다.
    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "INSERT IGNORE INTO orphaned_media (object_key) VALUES (:objectKey)", nativeQuery = true)
    void enqueue(@Param("objectKey") String objectKey);

    // 기록에 붙이려는 키가 지울 예정인지 본다. 정리 배치는 S3에서 지운 뒤에야 행을 없애므로, 지우는 동안에도 행이 남아 있다.
    boolean existsByObjectKeyIn(Collection<String> objectKeys);

    List<OrphanedMedia> findByIdGreaterThanOrderById(Long id, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM OrphanedMedia m WHERE m.objectKey IN :objectKeys")
    void deleteAllByObjectKeyIn(@Param("objectKeys") Collection<String> objectKeys);
}
