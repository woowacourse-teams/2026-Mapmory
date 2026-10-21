package com.mapmory.backend.admin.dashboard;

import com.mapmory.backend.admin.AdminTravelRecordQueryRepository;
import com.mapmory.backend.admin.dashboard.dto.AdminDashboardResponse;
import com.mapmory.backend.admin.dashboard.dto.AdminDashboardResponse.MemberStatistics;
import com.mapmory.backend.admin.dashboard.dto.AdminDashboardResponse.Period;
import com.mapmory.backend.admin.dashboard.dto.AdminDashboardResponse.PhotoStatistics;
import com.mapmory.backend.admin.dashboard.dto.AdminDashboardResponse.TravelRecordStatistics;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.MemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {

    private static final int DEFAULT_PERIOD_DAYS = 7;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final AdminTravelRecordQueryRepository travelRecordQueryRepository;
    private final Clock clock;

    public AdminDashboardService(
            MemberRepository memberRepository,
            AdminTravelRecordQueryRepository travelRecordQueryRepository,
            Clock clock
    ) {
        this.memberRepository = memberRepository;
        this.travelRecordQueryRepository = travelRecordQueryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate today = LocalDate.now(clock);
        LocalDate to = Objects.requireNonNullElse(requestedTo, today);
        LocalDate from = Objects.requireNonNullElse(requestedFrom, to.minusDays(DEFAULT_PERIOD_DAYS - 1));
        validateDateRange(from, to);

        LocalDateTime fromInclusive = toUtcDateTime(from);
        LocalDateTime toExclusive = toUtcDateTime(to.plusDays(1));
        return new AdminDashboardResponse(
                new Period(from, to),
                new MemberStatistics(
                        memberRepository.count(),
                        memberRepository.countByProvider(AuthProvider.GUEST),
                        memberRepository.countByProvider(AuthProvider.KAKAO),
                        memberRepository.countByProvider(AuthProvider.GOOGLE),
                        memberRepository.countByProvider(AuthProvider.APPLE),
                        memberRepository.countCreatedBetween(fromInclusive, toExclusive)
                ),
                new TravelRecordStatistics(
                        travelRecordQueryRepository.count(),
                        travelRecordQueryRepository.countCreatedBetween(fromInclusive, toExclusive)
                ),
                new PhotoStatistics(travelRecordQueryRepository.countAllMedia())
        );
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(AdminDashboardErrorCode.INVALID_DATE_RANGE);
        }
    }

    private LocalDateTime toUtcDateTime(LocalDate date) {
        return date.atStartOfDay(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
