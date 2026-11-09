package com.mapmory.backend.travelrecord.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.travelrecord.domain.MediaWithoutThumbnail;
import com.mapmory.backend.travelrecord.domain.ObjectKeys;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

class ThumbnailQueriesTest extends IntegrationTest {

    @Autowired
    private TravelRecordRepository travelRecordRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member jisu;
    private Region region;

    @BeforeEach
    void setUp() {
        jisu = memberRepository.save(Member.of("지수", UUID.randomUUID()));
        Long provinceId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM region WHERE region_type = 'PROVINCE'", Long.class);
        region = regionRepository.findById(provinceId).orElseThrow();
    }

    @Test
    void 썸네일이_없는_사진만_커서_뒤부터_id_순으로_읽는다() {
        List<Long> ids = mediaIds(record("a.jpg", "b.jpg", "c.jpg", "d.jpg"));
        jdbcTemplate.update("UPDATE record_media SET thumb_key = 'mapmory/derived/thumb/1/b.webp' WHERE id = ?", ids.get(1));
        jdbcTemplate.update("UPDATE record_media SET thumb_key = '' WHERE id = ?", ids.get(2));

        List<MediaWithoutThumbnail> page = travelRecordRepository.findMediaWithoutThumbnail(ids.get(0), PageRequest.of(0, 10));

        assertThat(page).extracting(MediaWithoutThumbnail::id).containsExactly(ids.get(2), ids.get(3));
        assertThat(page.getLast().objectKey()).endsWith("-d.jpg");
    }

    @Test
    void 썸네일은_비어_있을_때만_채운다() {
        Long id = mediaIds(record("a.jpg")).getFirst();

        assertThat(travelRecordRepository.attachThumbnail(id, "mapmory/derived/thumb/1/a.webp")).isEqualTo(1);
        assertThat(travelRecordRepository.attachThumbnail(id, "mapmory/derived/thumb/1/other.webp")).isZero();

        assertThat(thumbKeyOf(id)).isEqualTo("mapmory/derived/thumb/1/a.webp");
    }

    @Test
    void 빈_문자열_썸네일도_비어_있는_것으로_보고_채운다() {
        Long id = mediaIds(record("a.jpg")).getFirst();
        jdbcTemplate.update("UPDATE record_media SET thumb_key = '' WHERE id = ?", id);

        assertThat(travelRecordRepository.attachThumbnail(id, "mapmory/derived/thumb/1/a.webp")).isEqualTo(1);
    }

    @Test
    void 사진_행이_있는지_안다() {
        Long id = mediaIds(record("a.jpg")).getFirst();

        assertThat(travelRecordRepository.existsMediaById(id)).isTrue();
        assertThat(travelRecordRepository.existsMediaById(Long.MAX_VALUE)).isFalse();
    }

    @Test
    void 기준_시각보다_먼저_만들어졌는데_썸네일이_없는_사진을_센다() {
        List<Long> ids = mediaIds(record("old.jpg", "done.jpg", "new.jpg"));
        LocalDateTime now = LocalDateTime.of(2026, 11, 8, 12, 0);
        jdbcTemplate.update("UPDATE record_media SET created_at = ? WHERE id IN (?, ?)", now.minusHours(2), ids.get(0), ids.get(1));
        jdbcTemplate.update("UPDATE record_media SET created_at = ? WHERE id = ?", now, ids.get(2));
        jdbcTemplate.update("UPDATE record_media SET thumb_key = 'mapmory/derived/thumb/1/done.webp' WHERE id = ?", ids.get(1));

        assertThat(travelRecordRepository.countMediaWithoutThumbnailCreatedBefore(now.minusHours(1))).isEqualTo(1);
    }

    private TravelRecord record(String... fileNames) {
        TravelRecord record = TravelRecord.of(jisu, region, "도쿄", "본문", LocalDate.of(2026, 3, 1), null, LocalDate.of(2026, 11, 8));
        record.synchronizeMedia(ObjectKeys.from(IntStream.range(0, fileNames.length)
                .mapToObj(i -> "mapmory/travel-records/" + jisu.getId() + "/" + UUID.randomUUID() + "-" + fileNames[i])
                .toList()));
        return travelRecordRepository.save(record);
    }

    private List<Long> mediaIds(TravelRecord record) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM record_media WHERE travel_record_id = ? ORDER BY sort_order", Long.class, record.getId());
    }

    private String thumbKeyOf(Long id) {
        return jdbcTemplate.queryForObject("SELECT thumb_key FROM record_media WHERE id = ?", String.class, id);
    }
}
