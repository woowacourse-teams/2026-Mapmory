package com.mapmory.shared.presentation.triprecord

import com.mapmory.shared.presentation.date.toDatePickerLocalDate

internal fun endDatePickerMinimumDate(
    startDate: String?,
    today: String,
): String? {
    val todayDate = requireNotNull(today.toDatePickerLocalDate()) {
        "오늘 날짜 형식이 올바르지 않습니다: $today"
    }
    val selectedStartDate = startDate.toDatePickerLocalDate() ?: return null
    return minOf(todayDate, selectedStartDate).toString()
}

internal fun startDatePickerMaximumDate(
    endDate: String?,
    today: String,
): String {
    val todayDate = requireNotNull(today.toDatePickerLocalDate()) {
        "오늘 날짜 형식이 올바르지 않습니다: $today"
    }
    val selectedEndDate = endDate.toDatePickerLocalDate()
    return minOf(todayDate, selectedEndDate ?: todayDate).toString()
}

internal fun initialSelectableTripRecordDate(
    selectedDate: String?,
    fallbackDate: String,
    minimumDate: String? = null,
    maximumDate: String? = null,
): String {
    val fallback = requireNotNull(fallbackDate.toDatePickerLocalDate()) {
        "기본 날짜 형식이 올바르지 않습니다: $fallbackDate"
    }
    val minimum = minimumDate.toDatePickerLocalDate()
    val maximum = maximumDate.toDatePickerLocalDate()
    val selected = selectedDate.toDatePickerLocalDate() ?: fallback
    return selected
        .let { date -> minimum?.let { maxOf(date, it) } ?: date }
        .let { date -> maximum?.let { minOf(date, it) } ?: date }
        .toString()
}
