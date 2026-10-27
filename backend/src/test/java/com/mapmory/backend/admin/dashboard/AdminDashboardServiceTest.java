package com.mapmory.backend.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.travelrecord.domain.AdminTravelRecordQueryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AdminDashboardServiceTest {

    private final MemberRepository members = mock(MemberRepository.class);
    private final AdminTravelRecordQueryRepository records = mock(AdminTravelRecordQueryRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T15:30:00Z"), ZoneId.of("Asia/Seoul"));
    private final AdminDashboardService service = new AdminDashboardService(members, records, clock);

    @Test
    void 한국_날짜의_하루를_UTC_반개방_구간으로_조회한다() {
        LocalDate date = LocalDate.of(2026, 10, 13);
        service.getDashboard(date, date);

        LocalDateTime from = LocalDateTime.of(2026, 10, 12, 15, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 13, 15, 0);
        verify(members).countCreatedBetween(from, to);
        verify(records).countCreatedBetween(from, to);
    }

    @Test
    void UTC_날짜가_어제여도_한국_오늘을_포함한_최근_7일을_조회한다() {
        var response = service.getDashboard(null, null);

        assertThat(response.period().from()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(response.period().to()).isEqualTo(LocalDate.of(2026, 10, 14));
        verify(members).countCreatedBetween(
                LocalDateTime.of(2026, 10, 7, 15, 0), LocalDateTime.of(2026, 10, 14, 15, 0)
        );
    }

    // 운영의 Clock 빈은 UTC다. "오늘"은 Clock의 시간대가 아니라 한국 날짜로 정한다. (#502)
    @Test
    void 서버_Clock이_UTC여도_한국_오늘을_포함한_최근_7일을_조회한다() {
        Clock utcClock = Clock.fixed(Instant.parse("2026-10-13T15:30:00Z"), ZoneOffset.UTC);
        AdminDashboardService utcService = new AdminDashboardService(members, records, utcClock);

        var response = utcService.getDashboard(null, null);

        assertThat(response.period().to()).isEqualTo(LocalDate.of(2026, 10, 14));
        verify(members).countCreatedBetween(
                LocalDateTime.of(2026, 10, 7, 15, 0), LocalDateTime.of(2026, 10, 14, 15, 0)
        );
    }

    @Test
    void 가입_방식별_회원_수에_애플_회원을_포함한다() {
        when(members.countByProvider(AuthProvider.GUEST)).thenReturn(5L);
        when(members.countByProvider(AuthProvider.KAKAO)).thenReturn(4L);
        when(members.countByProvider(AuthProvider.GOOGLE)).thenReturn(3L);
        when(members.countByProvider(AuthProvider.APPLE)).thenReturn(2L);

        var members = service.getDashboard(null, null).members();

        assertThat(members.guest()).isEqualTo(5L);
        assertThat(members.kakao()).isEqualTo(4L);
        assertThat(members.google()).isEqualTo(3L);
        assertThat(members.apple()).isEqualTo(2L);
    }

    @Test
    void 시작일이_종료일보다_늦으면_집계하지_않는다() {
        assertThatThrownBy(() -> service.getDashboard(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 13)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AdminDashboardErrorCode.INVALID_DATE_RANGE));
        verifyNoInteractions(members, records);
    }
}
