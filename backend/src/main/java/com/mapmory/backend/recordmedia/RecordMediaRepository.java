package com.mapmory.backend.recordmedia;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecordMediaRepository extends JpaRepository<RecordMedia, Long> {

    List<RecordMedia> findByTravelRecordIdOrderBySortOrderAsc(Long travelRecordId);

    @Query("""
            select recordMedia.objectKey
            from RecordMedia recordMedia
            where recordMedia.travelRecord.id = :travelRecordId
            order by recordMedia.sortOrder asc
            """)
    List<String> findObjectKeysByTravelRecordId(@Param("travelRecordId") Long travelRecordId);

    List<RecordMedia> findByObjectKeyIn(Collection<String> objectKeys);
}
