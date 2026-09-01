package com.mapmory.shared.presentation.date

import android.app.DatePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Composable
actual fun PlatformDatePicker(
    visible: Boolean,
    initialDate: String?,
    minimumDate: String?,
    maximumDate: String?,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val latestOnDateSelected by rememberUpdatedState(onDateSelected)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    var dialog by remember { mutableStateOf<DatePickerDialog?>(null) }

    DisposableEffect(visible, initialDate, minimumDate, maximumDate) {
        if (!visible) {
            onDispose { }
        } else {
            val initial = initialDate.toDatePickerLocalDate()
                ?: Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            var callbackSent = false
            val picker = DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    callbackSent = true
                    latestOnDateSelected(LocalDate(year, month + 1, dayOfMonth).toString())
                    latestOnDismiss()
                },
                initial.year,
                initial.month.number - 1,
                initial.day,
            )
            minimumDate
                .toDatePickerLocalDate()
                ?.toDatePickerLocalEpochMillis()
                ?.let { picker.datePicker.minDate = it }
            maximumDate
                .toDatePickerLocalDate()
                ?.toDatePickerLocalEpochMillis()
                ?.let { picker.datePicker.maxDate = it }
            picker.setOnDismissListener {
                if (!callbackSent) latestOnDismiss()
                if (dialog === picker) dialog = null
            }
            dialog = picker
            picker.show()

            onDispose {
                callbackSent = true
                picker.setOnDismissListener(null)
                picker.dismiss()
                if (dialog === picker) dialog = null
            }
        }
    }
}

private fun LocalDate.toDatePickerLocalEpochMillis(): Long =
    atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
