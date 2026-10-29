package com.mapmory.backend.group.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.group.api.AlbumUpdated;
import com.mapmory.backend.group.domain.AlbumRepository;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.travelrecord.domain.ObjectKeys;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 앨범 열기·담기·이름과 지역 바꾸기(T4)를 HTTP부터 DB까지 확인한다.
 */
@RecordApplicationEvents
class AlbumIntegrationTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TravelGroupRepository travelGroupRepository;

    @Autowired
    private TravelRecordRepository travelRecordRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member jisu;
    private Member minho;
    private TravelGroup group;
    private Region seoul;
    private Region busan;

    @BeforeEach
    void setUp() {
        willAnswer(invocation -> new ExpiringUrl("https://cdn.test/" + invocation.getArgument(0), 300))
                .given(recordMediaUrlService).createViewUrl(anyString());
        jisu = socialMember("지수");
        minho = socialMember("민호");
        group = groupWith(jisu, minho);
        List<Long> provinceIds = jdbcTemplate.queryForList(
                "SELECT id FROM region WHERE region_type = 'PROVINCE' ORDER BY id LIMIT 2", Long.class);
        seoul = regionRepository.findById(provinceIds.get(0)).orElseThrow();
        busan = regionRepository.findById(provinceIds.get(1)).orElseThrow();
    }

    @Test
    void 내_기록으로_앨범을_열면_연_사람의_기록이_첫_항목이다() throws Exception {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3), "a.jpg", "b.jpg");

        open(jisu, group.getId(), record.getId(), "  도쿄 3월 ", busan.getId())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        startsWith("/api/v1/groups/" + group.getId() + "/albums/")))
                .andExpect(jsonPath("$.data.groupId").value(group.getId()))
                .andExpect(jsonPath("$.data.name").value("도쿄 3월"))
                .andExpect(jsonPath("$.data.region.id").value(busan.getId()))
                .andExpect(jsonPath("$.data.region.code").value(busan.getRegionCode()))
                .andExpect(jsonPath("$.data.region.type").value("PROVINCE"))
                .andExpect(jsonPath("$.data.region.name").value(busan.getName()))
                .andExpect(jsonPath("$.data.startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-03-03"))
                .andExpect(jsonPath("$.data.opener.memberId").value(jisu.getId()))
                .andExpect(jsonPath("$.data.opener.nickname").value("지수"))
                .andExpect(jsonPath("$.data.opener.me").value(true))
                .andExpect(jsonPath("$.data.myTravelRecordIds").value(contains(record.getId().intValue())))
                .andExpect(jsonPath("$.data.entries.length()").value(1))
                .andExpect(jsonPath("$.data.entries[0].travelRecordId").value(record.getId()))
                .andExpect(jsonPath("$.data.entries[0].owner.memberId").value(jisu.getId()))
                .andExpect(jsonPath("$.data.entries[0].title").value("도쿄"))
                .andExpect(jsonPath("$.data.entries[0].content").value("본문"))
                .andExpect(jsonPath("$.data.entries[0].addedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.entries[0].photos.length()").value(2))
                .andExpect(jsonPath("$.data.entries[0].photos[0].id").isNumber())
                .andExpect(jsonPath("$.data.entries[0].photos[0].viewUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.entries[0].photos[0].viewUrlExpiresIn").value(300))
                .andExpect(jsonPath("$.data.entries[0].photos[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.entries[0].photos[0].objectKey").doesNotExist());
    }

    @Test
    void 지역을_보내지_않으면_기록의_지역이다() throws Exception {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), null);

        open(jisu, group.getId(), record.getId(), "도쿄", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.region.id").value(seoul.getId()))
                .andExpect(jsonPath("$.data.endDate").value("2026-03-01"));
    }

    @Test
    void 남의_기록이나_없는_기록으로는_열_수_없다() throws Exception {
        TravelRecord minhoRecord = record(minho, LocalDate.of(2026, 3, 1), null);

        open(jisu, group.getId(), minhoRecord.getId(), "도쿄", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL_RECORD_NOT_FOUND"));
        open(jisu, group.getId(), Long.MAX_VALUE, "도쿄", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL_RECORD_NOT_FOUND"));
        assertThat(albumRepository.count()).isZero();
    }

    @Test
    void 없는_지역이면_404다() throws Exception {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), null);

        open(jisu, group.getId(), record.getId(), "도쿄", Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGION_NOT_FOUND"));
    }

    @Test
    void 이름이_규칙에_맞지_않으면_400이다() throws Exception {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), null);

        open(jisu, group.getId(), record.getId(), "가".repeat(201), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 이름을_비우면_기록_제목이고_제목도_비었으면_지역_이름이다() throws Exception {
        TravelRecord titled = record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg");
        TravelRecord untitled = travelRecordRepository.save(untitledRecord(jisu));

        open(jisu, group.getId(), titled.getId(), null, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("도쿄"));
        open(jisu, group.getId(), titled.getId(), " ", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("도쿄"));
        open(jisu, group.getId(), untitled.getId(), null, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(seoul.getName()));
    }

    @Test
    void 게스트는_앨범을_열_수_없다() throws Exception {
        Member guest = memberRepository.save(Member.ofGuest(UUID.randomUUID().toString(), "게스트", UUID.randomUUID()));
        TravelRecord record = record(guest, LocalDate.of(2026, 3, 1), null);

        open(guest, group.getId(), record.getId(), "도쿄", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_REQUIRED"));
    }

    @Test
    void 멤버가_아니면_그룹이_있어도_404다() throws Exception {
        Member stranger = socialMember("낯선 사람");
        TravelRecord record = record(stranger, LocalDate.of(2026, 3, 1), null);
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null));

        open(stranger, group.getId(), record.getId(), "도쿄", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        addEntry(stranger, group.getId(), albumId, record.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        update(stranger, group.getId(), albumId, "{\"name\": \"남의 앨범\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void 다른_그룹의_앨범은_내_그룹_경로로_찾을_수_없다() throws Exception {
        TravelGroup other = groupWith(jisu);
        Long otherAlbumId = openedAlbumId(jisu, other.getId(), record(jisu, LocalDate.of(2026, 3, 1), null));
        TravelRecord minhoRecord = record(minho, LocalDate.of(2026, 3, 2), null);

        addEntry(minho, group.getId(), otherAlbumId, minhoRecord.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
        update(jisu, group.getId(), otherAlbumId, "{\"name\": \"바꿈\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
    }

    @Test
    void 처음_담으면_201이고_같은_기록을_다시_보내면_200이다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)));
        TravelRecord minhoRecord = record(minho, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 4), "c.jpg");

        addEntry(minho, group.getId(), albumId, minhoRecord.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.startDate").value("2026-02-28"))
                .andExpect(jsonPath("$.data.endDate").value("2026-03-04"))
                .andExpect(jsonPath("$.data.opener.memberId").value(jisu.getId()))
                .andExpect(jsonPath("$.data.opener.me").value(false))
                .andExpect(jsonPath("$.data.myTravelRecordIds").value(contains(minhoRecord.getId().intValue())))
                // 항목은 기록 시작일 순이다. 나중에 담았지만 민호의 기록(2/28)이 지수의 기록(3/1)보다 앞이다.
                .andExpect(jsonPath("$.data.entries.length()").value(2))
                .andExpect(jsonPath("$.data.entries[0].owner.nickname").value("민호"))
                .andExpect(jsonPath("$.data.entries[0].owner.me").value(true))
                .andExpect(jsonPath("$.data.entries[0].addedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.entries[1].owner.nickname").value("지수"));
        addEntry(minho, group.getId(), albumId, minhoRecord.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(2));
    }

    @Test
    void 같은_멤버가_다른_기록도_담는다() throws Exception {
        TravelRecord shibuya = record(jisu, LocalDate.of(2026, 3, 1), null);
        TravelRecord asakusa = record(jisu, LocalDate.of(2026, 3, 2), null);
        Long albumId = openedAlbumId(jisu, shibuya);

        addEntry(jisu, group.getId(), albumId, asakusa.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.entries.length()").value(2))
                .andExpect(jsonPath("$.data.myTravelRecordIds").value(
                        contains(shibuya.getId().intValue(), asakusa.getId().intValue())))
                .andExpect(jsonPath("$.data.endDate").value("2026-03-02"));
    }

    @Test
    void 남의_기록은_담을_수_없다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null));

        addEntry(minho, group.getId(), albumId, record(jisu, LocalDate.of(2026, 3, 2), null).getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL_RECORD_NOT_FOUND"));
    }

    @Test
    void 멤버는_누구나_보낸_값만_바꾼다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null));

        update(minho, group.getId(), albumId, "{\"name\": \" 도쿄 봄 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("도쿄 봄"))
                .andExpect(jsonPath("$.data.region.id").value(seoul.getId()));
        update(minho, group.getId(), albumId, "{\"regionId\": " + busan.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("도쿄 봄"))
                .andExpect(jsonPath("$.data.region.id").value(busan.getId()));

        assertThat(albumRepository.findById(albumId).orElseThrow().getName()).isEqualTo("도쿄 봄");
    }

    @Test
    void 바꿀_값이_없거나_규칙에_맞지_않으면_400이다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null));

        update(jisu, group.getId(), albumId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        update(jisu, group.getId(), albumId, "{\"name\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 앨범_상세는_담긴_기록을_시작일_순으로_보여_준다() throws Exception {
        TravelRecord jisuRecord = record(jisu, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3), "a.jpg");
        Long albumId = openedAlbumId(jisu, jisuRecord);
        TravelRecord minhoRecord = record(minho, LocalDate.of(2026, 3, 2), null, "b.jpg", "c.jpg");
        addEntry(minho, group.getId(), albumId, minhoRecord.getId()).andExpect(status().isCreated());

        detail(minho, group.getId(), albumId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(albumId))
                .andExpect(jsonPath("$.data.startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-03-03"))
                .andExpect(jsonPath("$.data.opener.nickname").value("지수"))
                .andExpect(jsonPath("$.data.myTravelRecordIds").value(contains(minhoRecord.getId().intValue())))
                .andExpect(jsonPath("$.data.entries[0].travelRecordId").value(jisuRecord.getId()))
                .andExpect(jsonPath("$.data.entries[1].travelRecordId").value(minhoRecord.getId()))
                .andExpect(jsonPath("$.data.entries[1].endDate").doesNotExist())
                .andExpect(jsonPath("$.data.entries[1].photos.length()").value(2));
    }

    @Test
    void 지워진_기록은_상세에서_빠지고_기간도_남은_기록으로_계산한다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)));
        TravelRecord minhoRecord = record(minho, LocalDate.of(2026, 2, 20), LocalDate.of(2026, 3, 10), "b.jpg");
        addEntry(minho, group.getId(), albumId, minhoRecord.getId()).andExpect(status().isCreated());

        deleteRecord(minhoRecord);

        detail(minho, group.getId(), albumId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(1))
                .andExpect(jsonPath("$.data.startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-03-03"))
                .andExpect(jsonPath("$.data.myTravelRecordIds").isEmpty());
    }

    @Test
    void 멤버가_아니거나_다른_그룹의_앨범이면_상세를_볼_수_없다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null));
        TravelGroup other = groupWith(minho);

        detail(socialMember("낯선 사람"), group.getId(), albumId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        detail(minho, other.getId(), albumId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
    }

    @Test
    void 앨범_목록은_최근에_기록이_담긴_순서다() throws Exception {
        Long older = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 1, 1), null));
        Long newer = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 2, 1), null));
        setAddedAt(older, jisu, LocalDateTime.of(2026, 10, 1, 9, 0));
        setAddedAt(newer, jisu, LocalDateTime.of(2026, 10, 2, 9, 0));
        // 오래된 앨범에 기록이 새로 담기면 맨 앞으로 온다.
        addEntry(minho, group.getId(), older, record(minho, LocalDate.of(2026, 1, 1), null).getId())
                .andExpect(status().isCreated());
        setAddedAt(older, minho, LocalDateTime.of(2026, 10, 3, 9, 0));

        list(minho, group.getId(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(older))
                .andExpect(jsonPath("$.data.items[0].lastAddedAt").value("2026-10-03T09:00:00"))
                .andExpect(jsonPath("$.data.items[1].id").value(newer))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        list(minho, group.getId(), "?page=0&size=1")
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(older))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void 앨범_카드는_담긴_기록들로_기간과_사진_수와_대표_사진을_만든다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)));
        addEntry(minho, group.getId(), albumId,
                record(minho, LocalDate.of(2026, 2, 28), null, "m1.jpg", "m2.jpg").getId())
                .andExpect(status().isCreated());

        // 연 사람(지수)의 기록에 사진이 없으므로 먼저 담긴 기록 중 사진이 있는 민호의 첫 사진이다.
        list(jisu, group.getId(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("도쿄"))
                .andExpect(jsonPath("$.data.items[0].region.id").value(seoul.getId()))
                .andExpect(jsonPath("$.data.items[0].startDate").value("2026-02-28"))
                .andExpect(jsonPath("$.data.items[0].endDate").value("2026-03-03"))
                .andExpect(jsonPath("$.data.items[0].recordCount").value(2))
                .andExpect(jsonPath("$.data.items[0].photoCount").value(2))
                .andExpect(jsonPath("$.data.items[0].coverPhoto.viewUrl").value(endsWith("m1.jpg")))
                .andExpect(jsonPath("$.data.items[0].coverPhoto.viewUrlExpiresIn").value(300))
                .andExpect(jsonPath("$.data.items[0].contributors[0].memberId").value(jisu.getId()))
                .andExpect(jsonPath("$.data.items[0].contributors[0].nickname").value("지수"))
                .andExpect(jsonPath("$.data.items[0].contributors[0].me").value(true))
                .andExpect(jsonPath("$.data.items[0].contributors[1].nickname").value("민호"))
                .andExpect(jsonPath("$.data.items[0].contributors[1].me").value(false));
    }

    @Test
    void 기록을_여럿_담은_멤버는_담은_사람에_한_번만_나온다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null, "j1.jpg"));
        addEntry(minho, group.getId(), albumId, record(minho, LocalDate.of(2026, 3, 1), null, "m1.jpg").getId())
                .andExpect(status().isCreated());
        addEntry(jisu, group.getId(), albumId, record(jisu, LocalDate.of(2026, 3, 2), null, "j2.jpg").getId())
                .andExpect(status().isCreated());

        list(jisu, group.getId(), "")
                .andExpect(jsonPath("$.data.items[0].recordCount").value(3))
                .andExpect(jsonPath("$.data.items[0].photoCount").value(3))
                .andExpect(jsonPath("$.data.items[0].contributors.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].contributors[0].nickname").value("지수"))
                .andExpect(jsonPath("$.data.items[0].contributors[1].nickname").value("민호"))
                .andExpect(jsonPath("$.data.items[0].coverPhoto.viewUrl").value(endsWith("j1.jpg")));
    }

    @Test
    void 대표_사진은_연_사람_기록의_첫_사진이다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null, "j1.jpg"));
        addEntry(minho, group.getId(), albumId, record(minho, LocalDate.of(2026, 3, 1), null, "m1.jpg").getId())
                .andExpect(status().isCreated());

        list(minho, group.getId(), "")
                .andExpect(jsonPath("$.data.items[0].coverPhoto.viewUrl").value(endsWith("j1.jpg")));
    }

    @Test
    void 앨범이_없으면_빈_목록이고_페이지_크기는_50까지다() throws Exception {
        list(jisu, group.getId(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        list(jisu, group.getId(), "?size=51")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        list(jisu, group.getId(), "?page=-1")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 멤버가_아니거나_게스트면_목록을_볼_수_없다() throws Exception {
        Member guest = memberRepository.save(Member.ofGuest(UUID.randomUUID().toString(), "게스트", UUID.randomUUID()));

        list(socialMember("낯선 사람"), group.getId(), "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        list(guest, group.getId(), "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_REQUIRED"));
    }

    @Test
    void 그룹_목록의_대표_사진은_가장_최근에_기록이_담긴_앨범의_대표_사진이다() throws Exception {
        Long older = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 1, 1), null, "old.jpg"));
        Long newer = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 2, 1), null, "new.jpg"));
        setAddedAt(older, jisu, LocalDateTime.of(2026, 10, 1, 9, 0));
        setAddedAt(newer, jisu, LocalDateTime.of(2026, 10, 2, 9, 0));
        TravelGroup empty = groupWith(minho);

        mockMvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, bearer(minho)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(group.getId()))
                .andExpect(jsonPath("$.data[0].coverPhoto.viewUrl").value(endsWith("new.jpg")))
                .andExpect(jsonPath("$.data[0].coverPhoto.viewUrlExpiresIn").value(300))
                .andExpect(jsonPath("$.data[1].id").value(empty.getId()))
                .andExpect(jsonPath("$.data[1].coverPhoto").doesNotExist());
    }

    @Test
    void 기록을_지우면_그_기록의_항목만_빠지고_앨범은_남는다() throws Exception {
        TravelRecord jisuRecord = record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg");
        TravelRecord minhoRecord = record(minho, LocalDate.of(2026, 3, 2), null, "b.jpg");
        Long albumId = openedAlbumId(jisu, jisuRecord);
        addEntry(minho, group.getId(), albumId, minhoRecord.getId()).andExpect(status().isCreated());

        deleteRecordByApi(minho, minhoRecord).andExpect(status().isNoContent());

        assertThat(entryCount(albumId)).isEqualTo(1);
        detail(jisu, group.getId(), albumId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(1))
                .andExpect(jsonPath("$.data.entries[0].travelRecordId").value(jisuRecord.getId()));
    }

    @Test
    void 마지막_항목의_기록을_지우면_앨범도_지운다() throws Exception {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg");
        Long albumId = openedAlbumId(jisu, record);

        deleteRecordByApi(jisu, record).andExpect(status().isNoContent());

        assertThat(albumRepository.findById(albumId)).isEmpty();
        assertThat(entryCount(albumId)).isZero();
        list(jisu, group.getId(), "")
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 여러_그룹의_앨범에_담긴_기록을_지우면_모두에서_빠진다() throws Exception {
        TravelGroup other = groupWith(jisu);
        TravelRecord shared = record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg");
        TravelRecord kept = record(jisu, LocalDate.of(2026, 3, 2), null, "b.jpg");
        Long albumId = openedAlbumId(jisu, shared);
        addEntry(jisu, group.getId(), albumId, kept.getId()).andExpect(status().isCreated());
        Long otherAlbumId = openedAlbumId(jisu, other.getId(), shared);

        deleteRecordByApi(jisu, shared).andExpect(status().isNoContent());

        assertThat(entryCount(albumId)).isEqualTo(1);
        assertThat(albumRepository.findById(otherAlbumId)).isEmpty();
    }

    @Test
    void 나가면_내_항목만_내려가고_기록은_남는다() throws Exception {
        TravelRecord jisuRecord = record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg");
        TravelRecord minhoRecord = record(minho, LocalDate.of(2026, 3, 2), null, "b.jpg");
        Long albumId = openedAlbumId(jisu, jisuRecord);
        addEntry(minho, group.getId(), albumId, minhoRecord.getId()).andExpect(status().isCreated());

        leave(minho, group.getId()).andExpect(status().isNoContent());

        assertThat(entryCount(albumId)).isEqualTo(1);
        assertThat(travelRecordRepository.findById(minhoRecord.getId())).isPresent();
        assertThat(memberCountOf(group.getId())).isEqualTo(1);
        detail(minho, group.getId(), albumId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void 방장이_나가면_가장_먼저_들어온_멤버가_방장이_된다() throws Exception {
        leave(jisu, group.getId()).andExpect(status().isNoContent());

        assertThat(travelGroupRepository.findById(group.getId()).orElseThrow().getOwnerMemberId())
                .isEqualTo(minho.getId());
    }

    @Test
    void 연_사람이_나가도_다른_멤버의_항목이_있으면_앨범은_남고_연_사람이_넘어간다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg"));
        addEntry(minho, group.getId(), albumId, record(minho, LocalDate.of(2026, 3, 2), null, "b.jpg").getId())
                .andExpect(status().isCreated());

        leave(jisu, group.getId()).andExpect(status().isNoContent());

        detail(minho, group.getId(), albumId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(1))
                .andExpect(jsonPath("$.data.opener.nickname").value("민호"));
    }

    @Test
    void 내_항목만_있던_앨범은_나가면_지운다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg"));

        leave(jisu, group.getId()).andExpect(status().isNoContent());

        assertThat(albumRepository.findById(albumId)).isEmpty();
    }

    @Test
    void 마지막_멤버가_나가면_그룹과_앨범과_초대를_지운다() throws Exception {
        TravelGroup alone = groupWith(jisu);
        Long albumId = openedAlbumId(jisu, alone.getId(), record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg"));
        jdbcTemplate.update(
                "INSERT INTO group_invitation (group_id, token, code, inviter_member_id, expires_at) VALUES (?, ?, ?, ?, ?)",
                alone.getId(), UUID.randomUUID().toString(), "ABC123", jisu.getId(), LocalDateTime.now().plusDays(1));

        leave(jisu, alone.getId()).andExpect(status().isNoContent());

        assertThat(travelGroupRepository.findById(alone.getId())).isEmpty();
        assertThat(albumRepository.findById(albumId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_member WHERE group_id = ?", Long.class, alone.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_invitation WHERE group_id = ?", Long.class, alone.getId())).isZero();
    }

    @Test
    void 멤버가_아니면_나갈_수_없다() throws Exception {
        leave(socialMember("낯선 사람"), group.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        leave(jisu, Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void 탈퇴하면_모든_그룹에서_나가고_기록은_남는다() throws Exception {
        TravelRecord jisuRecord = record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg");
        Long albumId = openedAlbumId(jisu, jisuRecord);
        addEntry(minho, group.getId(), albumId, record(minho, LocalDate.of(2026, 3, 2), null, "b.jpg").getId())
                .andExpect(status().isCreated());
        TravelGroup alone = groupWith(jisu);
        Long aloneAlbumId = openedAlbumId(jisu, alone.getId(), record(jisu, LocalDate.of(2026, 4, 1), null, "c.jpg"));

        withdraw(jisu).andExpect(status().isNoContent());

        TravelGroup left = travelGroupRepository.findById(group.getId()).orElseThrow();
        assertThat(left.getOwnerMemberId()).isEqualTo(minho.getId());
        assertThat(memberCountOf(group.getId())).isEqualTo(1);
        detail(minho, group.getId(), albumId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(1))
                .andExpect(jsonPath("$.data.opener.nickname").value("민호"));
        assertThat(travelGroupRepository.findById(alone.getId())).isEmpty();
        assertThat(albumRepository.findById(aloneAlbumId)).isEmpty();
        // 개인 기록은 30일 뒤 파기한다(#435). 그룹에서만 빠진다.
        assertThat(travelRecordRepository.findById(jisuRecord.getId())).isPresent();
    }

    @Test
    void 탈퇴하지_않은_멤버는_그룹에_남는다() throws Exception {
        Member yuna = socialMember("유나");
        TravelGroup three = groupWith(jisu, minho, yuna);

        withdraw(minho).andExpect(status().isNoContent());

        assertThat(memberCountOf(three.getId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT member_id FROM group_member WHERE group_id = ?", Long.class, three.getId()))
                .containsExactlyInAnyOrder(jisu.getId(), yuna.getId());
        assertThat(travelGroupRepository.findById(three.getId()).orElseThrow().getOwnerMemberId())
                .isEqualTo(jisu.getId());
    }

    private ResultActions withdraw(Member member) throws Exception {
        return mockMvc.perform(delete("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private ResultActions leave(Member member, Long groupId) throws Exception {
        return mockMvc.perform(delete("/api/v1/groups/{groupId}/members/me", groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private int memberCountOf(Long groupId) {
        return jdbcTemplate.queryForObject("SELECT member_count FROM travel_group WHERE id = ?", Integer.class, groupId);
    }

    private ResultActions deleteRecordByApi(Member member, TravelRecord record) throws Exception {
        return mockMvc.perform(delete("/api/v1/travel-records/{id}", record.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private long entryCount(Long albumId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM album_entry WHERE album_id = ?", Long.class, albumId);
    }

    private void deleteRecord(TravelRecord record) {
        jdbcTemplate.update("DELETE FROM record_media WHERE travel_record_id = ?", record.getId());
        jdbcTemplate.update("DELETE FROM travel_record WHERE id = ?", record.getId());
    }

    private void setAddedAt(Long albumId, Member member, LocalDateTime addedAt) {
        jdbcTemplate.update("UPDATE album_entry SET created_at = ? WHERE album_id = ? AND member_id = ?",
                addedAt, albumId, member.getId());
    }

    private ResultActions detail(Member member, Long groupId, Long albumId) throws Exception {
        return mockMvc.perform(get("/api/v1/groups/{groupId}/albums/{albumId}", groupId, albumId)
                .header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private ResultActions list(Member member, Long groupId, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/groups/" + groupId + "/albums" + query)
                .header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private Member socialMember(String nickname) {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), nickname, UUID.randomUUID());
        member.changeNickname(nickname);
        return memberRepository.save(member);
    }

    private TravelGroup groupWith(Member owner, Member... others) {
        LocalDateTime joinedAt = LocalDateTime.now().minusMinutes(others.length + 1);
        TravelGroup created = travelGroupRepository.save(TravelGroup.create("도쿄 친구들", owner.getId(), joinedAt));
        for (Member other : others) {
            joinedAt = joinedAt.plusMinutes(1);
            jdbcTemplate.update("INSERT INTO group_member (group_id, member_id, joined_at) VALUES (?, ?, ?)",
                    created.getId(), other.getId(), joinedAt);
        }
        // 멤버를 직접 넣었으므로 인원도 맞춘다. 수락(join)은 인원을 함께 올린다.
        jdbcTemplate.update("UPDATE travel_group SET member_count = ? WHERE id = ?", others.length + 1, created.getId());
        return created;
    }

    // 제목은 선택이라 빈 제목으로 저장된 기록이다.
    private TravelRecord untitledRecord(Member member) {
        TravelRecord record = TravelRecord.of(member, seoul, "", "본문", LocalDate.of(2026, 3, 1), null, TODAY);
        record.synchronizeMedia(ObjectKeys.from(
                List.of("travel-records/" + member.getId() + "/" + UUID.randomUUID() + "-u.jpg")));
        return record;
    }

    // 사진 없는 기록은 이제 새로 만들 수 없지만(사진 1장 이상) 예전 기록으로 남아 있다. 그래서 사진이 없으면 붙이지 않는다.
    private TravelRecord record(Member member, LocalDate startDate, LocalDate endDate, String... fileNames) {
        TravelRecord record = TravelRecord.of(member, seoul, "도쿄", "본문", startDate, endDate, TODAY);
        if (fileNames.length > 0) {
            record.synchronizeMedia(ObjectKeys.from(Arrays.stream(fileNames)
                    .map(fileName -> "travel-records/" + member.getId() + "/" + UUID.randomUUID() + "-" + fileName)
                    .toList()));
        }
        return travelRecordRepository.save(record);
    }

    private Long openedAlbumId(Member member, TravelRecord record) throws Exception {
        return openedAlbumId(member, group.getId(), record);
    }

    private Long openedAlbumId(Member member, Long groupId, TravelRecord record) throws Exception {
        String body = open(member, groupId, record.getId(), "도쿄", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    @Test
    void 앨범을_열거나_새_기록을_담으면_올린_사람을_뺀_멤버에게_알릴_이벤트를_보낸다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg"));
        TravelRecord minhoRecord = record(minho, LocalDate.of(2026, 3, 2), null, "b.jpg");
        addEntry(minho, group.getId(), albumId, minhoRecord.getId()).andExpect(status().isCreated());

        assertThat(applicationEvents.stream(AlbumUpdated.class))
                .extracting(AlbumUpdated::albumId, AlbumUpdated::groupName, AlbumUpdated::albumName,
                        AlbumUpdated::actorMemberId, AlbumUpdated::recipientMemberIds)
                .containsExactly(
                        tuple(albumId, "도쿄 친구들", "도쿄", jisu.getId(), List.of(minho.getId())),
                        tuple(albumId, "도쿄 친구들", "도쿄", minho.getId(), List.of(jisu.getId())));
    }

    @Test
    void 같은_기록을_다시_담으면_알리지_않는다() throws Exception {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), null, "a.jpg");
        Long albumId = openedAlbumId(jisu, record);

        addEntry(jisu, group.getId(), albumId, record.getId()).andExpect(status().isOk());

        assertThat(applicationEvents.stream(AlbumUpdated.class)).hasSize(1);
    }

    private ResultActions open(Member member, Long groupId, Long travelRecordId, String name, Long regionId)
            throws Exception {
        String body = "{\"travelRecordId\": " + travelRecordId
                + (name == null ? "" : ", \"name\": \"" + name + "\"")
                + (regionId == null ? "" : ", \"regionId\": " + regionId) + "}";
        return mockMvc.perform(post("/api/v1/groups/{groupId}/albums", groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions addEntry(Member member, Long groupId, Long albumId, Long travelRecordId) throws Exception {
        return mockMvc.perform(post("/api/v1/groups/{groupId}/albums/{albumId}/entries", groupId, albumId)
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"travelRecordId\": " + travelRecordId + "}"));
    }

    private ResultActions update(Member member, Long groupId, Long albumId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/groups/{groupId}/albums/{albumId}", groupId, albumId)
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.issueAccessToken(member.getId());
    }
}
