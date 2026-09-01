package com.mapmory.shared.presentation.date

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformDatePicker(
    visible: Boolean,
    initialDate: String?,
    minimumDate: String?,
    maximumDate: String?,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    MaterialDatePickerFallback(
        visible = visible,
        initialDate = initialDate,
        minimumDate = minimumDate,
        maximumDate = maximumDate,
        onDateSelected = onDateSelected,
        onDismiss = onDismiss,
    )
}
