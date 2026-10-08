package com.mapmory.backend.admin.dashboard.dto;

import java.time.LocalDate;

public record AdminDashboardResponse(
        Period period,
        MemberStatistics members,
        TravelRecordStatistics travelRecords
) {

    public record Period(LocalDate from, LocalDate to) {
    }

    public record MemberStatistics(long total, long guest, long kakao, long newInPeriod) {
    }

    public record TravelRecordStatistics(long total, long createdInPeriod) {
    }
}
