package com.mapmory.backend.travelrecord;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 화면이 쓰는 여행 기록 집계 조회.
 *
 * <p>여행 기록 애그리거트의 상태를 바꾸지 않고 읽기만 하므로(개수 집계, 회원별 기록·사진 수 목록),
 * ADR 0017 결정 3의 조회 전용 리포지토리 예외에 따라 TravelRecordRepository와 따로 둔다.
 * 쓰기 메서드가 없도록 JpaRepository가 아닌 Repository를 상속한다.
 * 애그리거트 내부 엔티티(RecordMedia)를 쿼리하므로 travelrecord 패키지에 둔다.
 */
public interface AdminTravelRecordQueryRepository extends Repository<TravelRecord, Long> {

    interface MemberRecordCount {
        Long getMemberId();

        Long getRecordCount();
    }

    interface MemberRecordDetail {
        Long getRecordId();

        LocalDateTime getCreatedAt();

        Long getPhotoCount();
    }

    long count();

    @Query("SELECT COUNT(tr) FROM TravelRecord tr WHERE tr.createdAt >= :from AND tr.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(rm) FROM RecordMedia rm")
    long countAllMedia();

    @Query("""
        SELECT tr.member.id AS memberId, COUNT(tr.id) AS recordCount
        FROM TravelRecord tr
        WHERE tr.member.id IN :memberIds
        GROUP BY tr.member.id
    """)
    List<MemberRecordCount> countByMemberIds(@Param("memberIds") Collection<Long> memberIds);

    @Query(value = """
        SELECT tr.id AS recordId, tr.createdAt AS createdAt, COUNT(rm.id) AS photoCount
        FROM TravelRecord tr
        LEFT JOIN tr.media rm
        WHERE tr.member.id = :memberId
        GROUP BY tr.id, tr.createdAt
        ORDER BY tr.createdAt DESC, tr.id DESC
    """, countQuery = """
        SELECT COUNT(tr)
        FROM TravelRecord tr
        WHERE tr.member.id = :memberId
    """)
    Page<MemberRecordDetail> findAdminRecordDetailsByMemberId(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    @Query("SELECT COUNT(rm) FROM RecordMedia rm WHERE rm.travelRecord.member.id = :memberId")
    long countMediaByMemberId(@Param("memberId") Long memberId);
}
