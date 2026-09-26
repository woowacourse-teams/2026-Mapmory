package com.mapmory.shared.presentation.date

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
