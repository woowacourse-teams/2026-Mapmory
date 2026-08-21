package com.mapmory.backend.travelrecordtag;

import com.mapmory.backend.tag.Tag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordTagRepository extends JpaRepository<TravelRecordTag, Long> {
    @Modifying
    @Query("DELETE FROM TravelRecordTag trt WHERE trt.travelRecord.id = :travelRecordId")
    void deleteByTravelRecordId(@Param("travelRecordId") Long travelRecordId);

    @Query("""
            SELECT trt.tag
            FROM TravelRecordTag trt
            WHERE trt.travelRecord.id = :travelRecordId
            ORDER BY trt.tag.createdAt ASC, trt.tag.id ASC
            """)
    List<Tag> findTagsByTravelRecordId(@Param("travelRecordId") Long travelRecordId);

    @Query("""
            SELECT trt
            FROM TravelRecordTag trt
            JOIN FETCH trt.tag
            WHERE trt.travelRecord.id IN :travelRecordIds
            ORDER BY trt.tag.createdAt ASC, trt.tag.id ASC
            """)
    List<TravelRecordTag> findAllWithTagByTravelRecordIdIn(
            @Param("travelRecordIds") Collection<Long> travelRecordIds
    );
}
