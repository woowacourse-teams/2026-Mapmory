package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.support.MySqlTestContainerSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@DisplayName("관리자 여행 기록 집계 Repository")
class AdminTravelRecordQueryRepositoryTest extends MySqlTestContainerSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AdminTravelRecordQueryRepository repository;

    @Test
    @DisplayName("전체 기록 수, 기간 내 생성 수, 전체 사진 수를 센다")
    void countsRecordsAndMedia() {
        // 공유 컨테이너에 다른 데이터가 있을 수 있어 시나리오 전후 차이로 확인한다.
        LocalDateTime now = LocalDateTime.now();
        long recordsBefore = repository.count();
        long createdBefore = repository.countCreatedBetween(now.minusDays(1), now.plusDays(1));
        long mediaBefore = repository.countAllMedia();
        long futureBefore = repository.countCreatedBetween(now.plusDays(1), now.plusDays(2));

        createScenario();

        assertThat(repository.count() - recordsBefore).isEqualTo(3L);
        assertThat(repository.countCreatedBetween(now.minusDays(1), now.plusDays(1)) - createdBefore).isEqualTo(3L);
        assertThat(repository.countCreatedBetween(now.plusDays(1), now.plusDays(2))).isEqualTo(futureBefore);
        assertThat(repository.countAllMedia() - mediaBefore).isEqualTo(4L);
    }

    @Test
    @DisplayName("회원별 기록 수를 세고, 기록이 없는 회원은 결과에 넣지 않는다")
    void countsRecordsPerMember() {
        Scenario scenario = createScenario();

        Map<Long, Long> counts = repository.countByMemberIds(List.of(
                        scenario.member().getId(), scenario.otherMember().getId(), scenario.emptyMember().getId()))
                .stream()
                .collect(Collectors.toMap(
                        AdminTravelRecordQueryRepository.MemberRecordCount::getMemberId,
                        AdminTravelRecordQueryRepository.MemberRecordCount::getRecordCount
                ));

        assertThat(counts).containsOnly(
                Map.entry(scenario.member().getId(), 2L),
                Map.entry(scenario.otherMember().getId(), 1L)
        );
    }

    @Test
    @DisplayName("회원의 기록을 최신순으로 사진 수와 함께 페이지로 읽고, 회원의 사진 수를 센다")
    void readsMemberRecordDetails() {
        Scenario scenario = createScenario();

        // 한 페이지에 다 안 들어가게 크기를 1로 잡아 countQuery까지 실행되게 한다.
        Page<AdminTravelRecordQueryRepository.MemberRecordDetail> firstPage =
                repository.findAdminRecordDetailsByMemberId(scenario.member().getId(), PageRequest.of(0, 1));
        Page<AdminTravelRecordQueryRepository.MemberRecordDetail> secondPage =
                repository.findAdminRecordDetailsByMemberId(scenario.member().getId(), PageRequest.of(1, 1));

        assertThat(firstPage.getTotalElements()).isEqualTo(2L);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        // 같은 flush로 넣어 createdAt이 같을 수 있으므로 id 내림차순이 순서를 정한다.
        assertThat(firstPage.getContent())
                .singleElement()
                .satisfies(detail -> {
                    assertThat(detail.getRecordId()).isEqualTo(scenario.withoutPhotos().getId());
                    assertThat(detail.getPhotoCount()).isZero();
                });
        assertThat(secondPage.getContent())
                .singleElement()
                .satisfies(detail -> {
                    assertThat(detail.getRecordId()).isEqualTo(scenario.withPhotos().getId());
                    assertThat(detail.getPhotoCount()).isEqualTo(3L);
                });
        assertThat(repository.countMediaByMemberId(scenario.member().getId())).isEqualTo(3L);
        assertThat(repository.countMediaByMemberId(scenario.emptyMember().getId())).isZero();
    }

    private Scenario createScenario() {
        Member member = persist(Member.of("관리자 집계 회원", UUID.randomUUID()));
        Member otherMember = persist(Member.of("다른 회원", UUID.randomUUID()));
        Member emptyMember = persist(Member.of("기록 없는 회원", UUID.randomUUID()));
        Region japan = persist(Region.of(null, null, "ZQ", "테스트 국가", RegionType.COUNTRY));

        TravelRecord withPhotos = saveRecord(member, japan);
        TravelRecord withoutPhotos = saveRecord(member, japan);
        TravelRecord otherRecord = saveRecord(otherMember, japan);
        saveMedia(withPhotos, 3);
        saveMedia(otherRecord, 1);
        flushAndClear();

        return new Scenario(member, otherMember, emptyMember, withPhotos, withoutPhotos);
    }

    private TravelRecord saveRecord(Member member, Region region) {
        return persist(TravelRecord.of(member, region, "기록 " + UUID.randomUUID(), "내용", LocalDate.of(2026, 8, 31), null));
    }

    private void saveMedia(TravelRecord travelRecord, int count) {
        for (int sequence = 0; sequence < count; sequence++) {
            persist(RecordMedia.of(travelRecord, "travel-records/test/" + UUID.randomUUID() + ".jpg", null, sequence));
        }
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private record Scenario(
            Member member,
            Member otherMember,
            Member emptyMember,
            TravelRecord withPhotos,
            TravelRecord withoutPhotos
    ) {
    }
}
