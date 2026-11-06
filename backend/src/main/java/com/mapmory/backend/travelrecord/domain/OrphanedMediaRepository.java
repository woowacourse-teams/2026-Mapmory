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
    // 사진을 뺐다가 다시 붙이고 또 빼면 같은 키가 또 오므로 무시한다.
    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "INSERT IGNORE INTO orphaned_media (object_key) VALUES (:objectKey)", nativeQuery = true)
    void enqueue(@Param("objectKey") String objectKey);

    List<OrphanedMedia> findByIdGreaterThanOrderById(Long id, Pageable pageable);

    // 기록에 붙지 않은 객체를 대조할 때, 이미 큐에 있는 키는 후보로 세지 않는다. (#186)
    @Query("SELECT m.objectKey FROM OrphanedMedia m WHERE m.objectKey IN :objectKeys")
    List<String> findObjectKeysIn(@Param("objectKeys") Collection<String> objectKeys);

    @Modifying
    @Transactional
    @Query("DELETE FROM OrphanedMedia m WHERE m.objectKey IN :objectKeys")
    void deleteAllByObjectKeyIn(@Param("objectKeys") Collection<String> objectKeys);
}
