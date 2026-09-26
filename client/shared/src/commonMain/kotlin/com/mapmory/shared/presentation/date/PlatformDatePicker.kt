package com.mapmory.shared.presentation.date

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Opens the platform date picker when [visible] is true.
 *
 * Android and iOS use their native date controls. The JVM target, which is used by previews and
 * tests, provides the common Material 3 picker as a fallback.
 */
@Composable
expect fun PlatformDatePicker(
    visible: Boolean,
    initialDate: String?,
    minimumDate: String?,
    maximumDate: String? = null,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
)

private enum class DateRangeSelectionStep {
    START,
    WAITING_FOR_END_PICKER,
    END,
}

/** 기존 플랫폼 달력을 유지하면서 시작일 선택 직후 종료일 선택을 이어서 연다. */
@Composable
fun PlatformDateRangePicker(
    visible: Boolean,
    initialStartDate: String?,
    initialEndDate: String?,
    minimumDate: String? = null,
    maximumDate: String? = null,
    onDatesSelected: (startDate: String, endDate: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var stepName by rememberSaveable { mutableStateOf(DateRangeSelectionStep.START.name) }
    var selectedStartDate by rememberSaveable { mutableStateOf<String?>(null) }
    val step = DateRangeSelectionStep.valueOf(stepName)

    LaunchedEffect(visible, stepName) {
        if (!visible) {
            stepName = DateRangeSelectionStep.START.name
            selectedStartDate = null
        } else if (step == DateRangeSelectionStep.WAITING_FOR_END_PICKER) {
            delay(NativeRangePickerTransitionMillis)
            stepName = DateRangeSelectionStep.END.name
        }
    }
    if (!visible) return

    val startDate = selectedStartDate ?: initialStartDate
    val startLocalDate = startDate.toDatePickerLocalDate()
    val configuredMinimumDate = minimumDate.toDatePickerLocalDate()
    val endMinimumDate = listOfNotNull(configuredMinimumDate, startLocalDate)
        .maxOrNull()
        ?.toString()
    val validInitialEndDate = initialEndDate
        .toDatePickerLocalDate()
        ?.takeIf { end -> startLocalDate == null || end >= startLocalDate }
        ?.toString()

    PlatformDatePicker(
        visible = step != DateRangeSelectionStep.WAITING_FOR_END_PICKER,
        initialDate = if (step == DateRangeSelectionStep.START) {
            initialStartDate
        } else {
            validInitialEndDate ?: startDate
        },
        minimumDate = if (step == DateRangeSelectionStep.START) minimumDate else endMinimumDate,
        maximumDate = maximumDate,
        onDateSelected = { date ->
            if (step == DateRangeSelectionStep.START) {
                selectedStartDate = date
                stepName = DateRangeSelectionStep.WAITING_FOR_END_PICKER.name
            } else {
                onDatesSelected(requireNotNull(startDate), date)
            }
        },
        onDismiss = {
            if (
                DateRangeSelectionStep.valueOf(stepName) !=
                DateRangeSelectionStep.WAITING_FOR_END_PICKER
            ) {
                stepName = DateRangeSelectionStep.START.name
                selectedStartDate = null
                onDismiss()
            }
        },
    )
}

private const val NativeRangePickerTransitionMillis = 250L

internal fun String?.toDatePickerLocalDate(): LocalDate? = runCatching {
    this
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.replace('.', '-')
        ?.let(LocalDate::parse)
}.getOrNull()

internal fun LocalDate.toDatePickerEpochMillis(): Long =
    atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

internal fun Long.toDatePickerString(): String =
    Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.UTC)
        .date
        .toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaterialDatePickerFallback(
    visible: Boolean,
    initialDate: String?,
    minimumDate: String?,
    maximumDate: String?,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val minimumDateMillis = minimumDate
        .toDatePickerLocalDate()
        ?.toDatePickerEpochMillis()
    val maximumDateMillis = maximumDate
        .toDatePickerLocalDate()
        ?.toDatePickerEpochMillis()
    val selectableDates = remember(minimumDateMillis, maximumDateMillis) {
        if (minimumDateMillis == null && maximumDateMillis == null) {
            DatePickerDefaults.AllDates
        } else {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    (minimumDateMillis == null || utcTimeMillis >= minimumDateMillis) &&
                        (maximumDateMillis == null || utcTimeMillis <= maximumDateMillis)
            }
        }
    }

    key(initialDate, minimumDate, maximumDate) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialDate
                .toDatePickerLocalDate()
                ?.toDatePickerEpochMillis(),
            selectableDates = selectableDates,
        )

        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis
                            ?.toDatePickerString()
                            ?.let(onDateSelected)
                        onDismiss()
                    },
                ) {
                    Text("확인")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
