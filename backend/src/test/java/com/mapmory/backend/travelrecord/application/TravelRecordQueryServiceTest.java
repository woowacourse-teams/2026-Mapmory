package com.mapmory.backend.travelrecord.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.support.SqlStatementCounter;
import com.mapmory.backend.travelrecord.api.RecordBrief;
import com.mapmory.backend.travelrecord.api.RecordView;
import com.mapmory.backend.travelrecord.api.TravelRecordQuery;
import com.mapmory.backend.travelrecord.domain.ObjectKeys;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TravelRecordQueryServiceTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Autowired
    private TravelRecordQuery travelRecordQuery;

    @Autowired
    private TravelRecordRepository travelRecordRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Member jisu;
    private Region region;

    @BeforeEach
    void setUp() {
        willAnswer(invocation -> new ExpiringUrl("https://cdn.test/" + invocation.getArgument(0), 300))
                .given(recordMediaUrlService).createViewUrl(anyString());
        jisu = memberRepository.save(Member.of("지수", UUID.randomUUID()));
        Long provinceId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM region WHERE region_type = 'PROVINCE'", Long.class);
        region = regionRepository.findById(provinceId).orElseThrow();
    }

    @Test
    void 내_기록이면_요약을_주고_남의_기록이면_빈_값이다() {
        Member minho = memberRepository.save(Member.of("민호", UUID.randomUUID()));
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg", "b.jpg");

        assertThat(travelRecordQuery.findOwned(record.getId(), jisu.getId())).hasValueSatisfying(brief -> {
            assertThat(brief.memberId()).isEqualTo(jisu.getId());
            assertThat(brief.regionId()).isEqualTo(region.getId());
            assertThat(brief.photoCount()).isEqualTo(2);
            assertThat(brief.cover()).isNotNull();
        });
        assertThat(travelRecordQuery.findOwned(record.getId(), minho.getId())).isEmpty();
        assertThat(travelRecordQuery.findOwned(Long.MAX_VALUE, jisu.getId())).isEmpty();
    }

    @Test
    void 요약의_종료일은_없으면_시작일이고_사진이_없으면_표지도_없다() {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), null);

        RecordBrief brief = travelRecordQuery.briefsOf(List.of(record.getId())).getFirst();

        assertThat(brief.endDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(brief.photoCount()).isZero();
        assertThat(brief.cover()).isNull();
    }

    @Test
    void 표지는_썸네일이_있으면_썸네일을_서명한다() {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg");
        jdbcTemplate.update("UPDATE record_media SET thumb_key = 'thumbs/a.jpg' WHERE travel_record_id = ?",
                record.getId());

        RecordBrief brief = travelRecordQuery.briefsOf(List.of(record.getId())).getFirst();

        assertThat(brief.cover().url()).contains("thumbs/a.jpg");
    }

    @Test
    void 없는_id는_오류_없이_빠지고_사진은_정렬_순서대로다() {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3), "c.jpg", "a.jpg", "b.jpg");

        List<RecordView> views = travelRecordQuery.viewsOf(List.of(record.getId(), Long.MAX_VALUE));

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.endDate()).isEqualTo(LocalDate.of(2026, 3, 3));
            assertThat(view.photos()).extracting(RecordView.PhotoView::sortOrder).containsExactly(0, 1, 2);
            assertThat(view.photos().getFirst().viewUrl().url()).contains("c.jpg");
        });
    }

    @Test
    void 기록이_몇_개든_SQL_수는_같다() {
        List<Long> one = List.of(record(jisu, LocalDate.of(2026, 3, 1), null, "one.jpg").getId());
        List<Long> ten = IntStream.range(0, 10)
                .mapToObj(i -> record(memberRepository.save(Member.of("회원" + i, UUID.randomUUID())),
                        LocalDate.of(2026, 3, 1), null, "p" + i + ".jpg").getId())
                .toList();
        SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);

        long forOne = counter.count(() -> travelRecordQuery.viewsOf(one));
        long forTen = counter.count(() -> travelRecordQuery.viewsOf(ten));

        assertThat(forTen).isEqualTo(forOne).isEqualTo(2);
        assertThat(counter.count(() -> travelRecordQuery.briefsOf(ten))).isEqualTo(2);
    }

    // 사진 없는 기록은 이제 새로 만들 수 없지만(사진 1장 이상) 예전 기록으로 남아 있다. 그래서 사진이 없으면 붙이지 않는다.
    private TravelRecord record(Member member, LocalDate startDate, LocalDate endDate, String... fileNames) {
        TravelRecord record = TravelRecord.of(member, region, "도쿄", "본문", startDate, endDate, TODAY);
        if (fileNames.length > 0) {
            record.synchronizeMedia(ObjectKeys.from(IntStream.range(0, fileNames.length)
                    .mapToObj(i -> "travel-records/" + member.getId() + "/" + UUID.randomUUID() + "-" + fileNames[i])
                    .toList()));
        }
        return travelRecordRepository.save(record);
    }
}
