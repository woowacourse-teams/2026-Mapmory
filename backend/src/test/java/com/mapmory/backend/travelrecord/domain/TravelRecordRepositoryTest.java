package com.mapmory.backend.travelrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.region.domain.RegionType;
import com.mapmory.backend.support.MySqlTestContainerSupport;
import com.mapmory.backend.tag.Tag;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
class TravelRecordRepositoryTest extends MySqlTestContainerSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Autowired
    private TravelRecordRepository travelRecordRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 여행_일지를_저장한다() {
        Member member = memberRepository.save(Member.of("테스터", UUID.randomUUID()));
        Region testCountry = regionRepository.save(
                Region.of(null, null, "ZZ", "테스트 국가", RegionType.COUNTRY)
        );

        TravelRecord travelRecord = travelRecordRepository.save(
                TravelRecord.of(
                        member,
                        testCountry,
                        "테스트 여행",
                        "",
                        LocalDate.of(2026, 8, 11),
                        null,
                        TODAY
                )
        );

        entityManager.flush();
        entityManager.clear();

        assertThat(travelRecord.getId()).isNotNull();
        assertThat(travelRecordRepository.findById(travelRecord.getId())).isPresent()
                .get().extracting(TravelRecord::getPlace).isNull();
    }

    @Test
    void 기록_장소를_하나의_값_객체로_저장하고_다시_읽는다() {
        Member member = memberRepository.save(Member.of("장소 기록 회원", UUID.randomUUID()));
        Region country = regionRepository.save(
                Region.of(null, null, "ZP", "장소 테스트 국가", RegionType.COUNTRY)
        );
        TravelRecord travelRecord = TravelRecord.of(
                member, country, "공원 방문", "", LocalDate.of(2026, 8, 11), null, TODAY
        );
        RecordedPlace place = new RecordedPlace(
                "GEOAPIFY", "park-1", "여의도한강공원",
                "© OpenStreetMap contributors", "https://www.openstreetmap.org/copyright"
        );
        travelRecord.setPlace(place);
        travelRecordRepository.save(travelRecord);

        entityManager.flush();
        entityManager.clear();

        assertThat(travelRecordRepository.findById(travelRecord.getId()))
                .get().extracting(TravelRecord::getPlace).isEqualTo(place);
    }

    @Test
    void 회원의_여행_일지를_페이지로_조회한다() {
        Member member = memberRepository.save(
                Member.of("테스터", UUID.randomUUID())
        );

        Region country = regionRepository.save(
                Region.of(null, null, "XX", "테스트 국가", RegionType.COUNTRY)
        );

        travelRecordRepository.save(
                TravelRecord.of(
                        member,
                        country,
                        "첫 번째 기록",
                        "",
                        LocalDate.of(2026, 8, 10),
                        null,
                        TODAY
                )
        );

        Page<TravelRecord> result = travelRecordRepository.findByMemberIdAndOptionalTagId(
                member.getId(),
                null,
                PageRequest.of(
                        0, // 첫 페이지
                        20, // 최대 20개
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 선택한_태그가_연결된_여행_일지만_조회한다() {
        Member member = memberRepository.save(Member.of("태그 조회 회원", UUID.randomUUID()));
        Region country = regionRepository.save(
                Region.of(null, null, "XT", "태그 조회 국가", RegionType.COUNTRY)
        );
        Tag tag = Tag.of(member, "연인");
        entityManager.persist(tag);
        TravelRecord taggedRecord = travelRecordRepository.save(TravelRecord.of(
                member, country, "태그 기록", "", LocalDate.of(2026, 8, 10), null, TODAY
        ));
        travelRecordRepository.save(TravelRecord.of(
                member, country, "태그 없는 기록", "", LocalDate.of(2026, 8, 11), null, TODAY
        ));
        taggedRecord.retag(RecordTagIds.from(List.of(tag.getId())), List.of(tag));
        entityManager.flush();
        entityManager.clear();

        Page<TravelRecord> result = travelRecordRepository.findByMemberIdAndOptionalTagId(
                member.getId(),
                tag.getId(),
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent())
                .extracting(TravelRecord::getTitle)
                .containsExactly("태그 기록");
    }

    @Test
    void 소유한_일지와_정렬된_미디어를_조회한다() {
        Member owner = memberRepository.save(Member.of("작성자", UUID.randomUUID()));
        Member otherMember = memberRepository.save(Member.of("다른 회원", UUID.randomUUID()));
        Region country = regionRepository.save(
                Region.of(null, null, "YY", "상세 조회 국가", RegionType.COUNTRY)
        );
        TravelRecord travelRecord = travelRecordRepository.save(
                TravelRecord.of(
                        owner,
                        country,
                        "상세 조회 기록",
                        "본문",
                        LocalDate.of(2026, 8, 11),
                        null,
                        TODAY
                )
        );
        travelRecord.synchronizeMedia(ObjectKeys.from(
                java.util.List.of("mapmory/detail/a.jpg", "mapmory/detail/b.jpg")
        ));

        entityManager.flush();
        entityManager.clear();

        assertThat(travelRecordRepository.findByIdAndMemberId(travelRecord.getId(), owner.getId()))
                .isPresent();
        assertThat(travelRecordRepository.findByIdAndMemberId(travelRecord.getId(), otherMember.getId()))
                .isEmpty();
        assertThat(travelRecordRepository.findByIdAndMemberId(travelRecord.getId(), owner.getId())
                .orElseThrow()
                .getMedia())
                .extracting(RecordMedia::getObjectKey)
                .containsExactly("mapmory/detail/a.jpg", "mapmory/detail/b.jpg");
        assertThat(travelRecordRepository.findMediaByTravelRecordIdIn(
                java.util.List.of(travelRecord.getId())
        ))
                .extracting(RecordMedia::getObjectKey)
                .containsExactly("mapmory/detail/a.jpg", "mapmory/detail/b.jpg");
        assertThat(travelRecordRepository.existsMediaByObjectKeyIn(
                java.util.List.of("mapmory/detail/a.jpg")
        )).isTrue();
        assertThat(travelRecordRepository.existsMediaByObjectKeyIn(
                java.util.List.of("mapmory/detail/없는키.jpg")
        )).isFalse();
    }

    @Test
    void 여행_일지를_삭제하면_연결된_미디어도_삭제한다() {
        Member member = memberRepository.save(Member.of("삭제 테스트 회원", UUID.randomUUID()));
        Region country = regionRepository.save(
                Region.of(null, null, "XD", "삭제 테스트 국가", RegionType.COUNTRY)
        );
        TravelRecord travelRecord = travelRecordRepository.save(
                TravelRecord.of(
                        member,
                        country,
                        "삭제할 기록",
                        "본문",
                        LocalDate.of(2026, 8, 16),
                        null,
                        TODAY
                )
        );
        travelRecord.synchronizeMedia(ObjectKeys.from(java.util.List.of("mapmory/delete/a.jpg")));
        entityManager.flush();
        entityManager.clear();

        Long travelRecordId = travelRecord.getId();
        TravelRecord foundTravelRecord = travelRecordRepository.findById(travelRecordId)
                .orElseThrow();

        travelRecordRepository.delete(foundTravelRecord);
        entityManager.flush();
        entityManager.clear();

        assertThat(travelRecordRepository.findById(travelRecordId)).isEmpty();
        assertThat(travelRecordRepository.findMediaByTravelRecordIdIn(
                java.util.List.of(travelRecordId)
        )).isEmpty();
    }

    @Test
    void 태그를_유지하고_빼고_더하는_변경이_한_번의_flush로_저장된다() {
        Member member = memberRepository.save(Member.of("태그 교체 회원", UUID.randomUUID()));
        Region country = regionRepository.save(
                Region.of(null, null, "XR", "태그 교체 국가", RegionType.COUNTRY)
        );
        Tag kept = persistTag(member, "유지");
        Tag removed = persistTag(member, "제거");
        Tag added = persistTag(member, "추가");
        TravelRecord travelRecord = TravelRecord.of(
                member, country, "태그 교체", "", LocalDate.of(2026, 8, 11), null, TODAY
        );
        travelRecord.retag(RecordTagIds.from(List.of(kept.getId(), removed.getId())), List.of(kept, removed));
        Long travelRecordId = travelRecordRepository.save(travelRecord).getId();
        entityManager.flush();
        entityManager.clear();

        // 남는 태그를 지웠다 다시 붙이면 INSERT가 DELETE보다 먼저 나가 UNIQUE(tag_id, travel_record_id)에 걸린다.
        TravelRecord found = travelRecordRepository.findByIdAndMemberId(travelRecordId, member.getId())
                .orElseThrow();
        found.retag(
                RecordTagIds.from(List.of(kept.getId(), added.getId())),
                List.of(entityManager.find(Tag.class, kept.getId()), entityManager.find(Tag.class, added.getId()))
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(travelRecordRepository.findTagsByTravelRecordId(travelRecordId))
                .extracting(Tag::getName)
                .containsExactly("유지", "추가");
        assertThat(travelRecordRepository.findTagLinksByTravelRecordIdIn(List.of(travelRecordId)))
                .extracting(link -> link.getTag().getName())
                .containsExactly("유지", "추가");
    }

    @Test
    void 여행_일지를_삭제하면_태그_연결도_삭제하고_태그는_남긴다() {
        Member member = memberRepository.save(Member.of("태그 삭제 회원", UUID.randomUUID()));
        Region country = regionRepository.save(
                Region.of(null, null, "XQ", "태그 삭제 국가", RegionType.COUNTRY)
        );
        Tag tag = persistTag(member, "가족");
        TravelRecord travelRecord = TravelRecord.of(
                member, country, "삭제할 기록", "", LocalDate.of(2026, 8, 11), null, TODAY
        );
        travelRecord.retag(RecordTagIds.from(List.of(tag.getId())), List.of(tag));
        Long travelRecordId = travelRecordRepository.save(travelRecord).getId();
        entityManager.flush();
        entityManager.clear();

        travelRecordRepository.delete(travelRecordRepository.findById(travelRecordId).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(travelRecordRepository.findTagLinksByTravelRecordIdIn(List.of(travelRecordId))).isEmpty();
        assertThat(entityManager.find(Tag.class, tag.getId())).isNotNull();
    }

    private Tag persistTag(Member member, String name) {
        Tag tag = Tag.of(member, name);
        entityManager.persist(tag);
        return tag;
    }

    @Test
    void 지역_안의_기록은_그_아래_지역의_기록까지_포함한다() {
        Member member = memberRepository.save(Member.of("지역 필터 회원", UUID.randomUUID()));
        Region country = regionRepository.save(Region.of(null, null, "XR", "필터 국가", RegionType.COUNTRY));
        Region province = regionRepository.save(Region.of(country, country, "X1", "필터 시도", RegionType.PROVINCE));
        Region otherProvince = regionRepository.save(
                Region.of(country, country, "X2", "다른 시도", RegionType.PROVINCE));
        Region district = regionRepository.save(
                Region.of(province, country, "X1001", "필터 시군구", RegionType.DISTRICT));
        TravelRecord inCountry = save(member, country, "국가 기록");
        TravelRecord inProvince = save(member, province, "시도 기록");
        TravelRecord inOtherProvince = save(member, otherProvince, "다른 시도 기록");
        TravelRecord inDistrict = save(member, district, "시군구 기록");
        entityManager.flush();
        entityManager.clear();

        assertThat(idsInRegion(member, country))
                .containsExactlyInAnyOrder(inCountry.getId(), inProvince.getId(), inOtherProvince.getId(),
                        inDistrict.getId());
        assertThat(idsInRegion(member, province)).containsExactlyInAnyOrder(inProvince.getId(), inDistrict.getId());
        assertThat(idsInRegion(member, district)).containsExactly(inDistrict.getId());
    }

    private TravelRecord save(Member member, Region region, String title) {
        return travelRecordRepository.save(
                TravelRecord.of(member, region, title, "", LocalDate.of(2026, 8, 11), null, TODAY));
    }

    private java.util.List<Long> idsInRegion(Member member, Region region) {
        return travelRecordRepository.findByMemberIdInRegion(member.getId(), region, null, PageRequest.of(0, 20))
                .map(TravelRecord::getId)
                .getContent();
    }
}
