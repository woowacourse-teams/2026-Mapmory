package com.mapmory.shared.presentation.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.domain.model.ServiceFeedbackCategory
import com.mapmory.shared.domain.model.ServiceFeedbackPlatform
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette
import kotlinx.coroutines.launch

@Composable
internal fun ServiceFeedbackDialog(
    platform: ServiceFeedbackPlatform,
    onDismiss: () -> Unit,
    onSubmit: suspend (ServiceFeedbackCategory, String) -> Result<Unit>,
) {
    var category by remember { mutableStateOf(ServiceFeedbackCategory.OTHER) }
    var content by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = TripRecordPalette.current.surface,
        title = { Text("의견 보내기", color = TripRecordPalette.current.headingText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (platform == ServiceFeedbackPlatform.IOS) "iOS 앱에서 보내는 의견" else "Android 앱에서 보내는 의견",
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 12.sp,
                )
                Text("카테고리", color = TripRecordPalette.current.bodyText, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServiceFeedbackCategory.entries.chunked(2).forEach { rowCategories ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowCategories.forEach { option ->
                                val selected = category == option
                                val shape = RoundedCornerShape(12.dp)
                                Text(
                                    text = option.label,
                                    color = if (selected) TripRecordPalette.current.primary else TripRecordPalette.current.bodyText,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(shape)
                                        .background(if (selected) TripRecordPalette.current.primarySoft else Color.Transparent)
                                        .border(
                                            1.dp,
                                            if (selected) TripRecordPalette.current.primary else TripRecordPalette.current.border,
                                            shape,
                                        )
                                        .selectable(
                                            selected = selected,
                                            enabled = !isSubmitting && !submitted,
                                            role = Role.RadioButton,
                                        ) {
                                            category = option
                                            errorMessage = null
                                        }
                                        .padding(vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
                Text("내용", color = TripRecordPalette.current.bodyText, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = content,
                    onValueChange = { value ->
                        content = value.take(MaxServiceFeedbackLength)
                        errorMessage = null
                    },
                    enabled = !isSubmitting && !submitted,
                    placeholder = { Text("불편한 점이나 바라는 점을 적어주세요.") },
                    minLines = 4,
                    maxLines = 6,
                    supportingText = { Text("${content.length}/$MaxServiceFeedbackLength") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TripRecordPalette.current.primary,
                        unfocusedBorderColor = TripRecordPalette.current.border,
                        disabledBorderColor = TripRecordPalette.current.border,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    submitted -> Text("의견을 보냈어요. 소중한 의견 감사합니다.", color = TripRecordPalette.current.primary, fontSize = 13.sp)
                    errorMessage != null -> Text(errorMessage.orEmpty(), color = TripRecordPalette.current.danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (submitted) {
                TextButton(onClick = onDismiss) { Text("완료", color = TripRecordPalette.current.primary) }
            } else {
                TextButton(
                    enabled = content.isNotBlank() && !isSubmitting,
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            onSubmit(category, content.trim())
                                .onSuccess { submitted = true }
                                .onFailure { errorMessage = it.message ?: "의견을 보내지 못했어요. 다시 시도해 주세요." }
                            isSubmitting = false
                        }
                    },
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp).align(Alignment.CenterVertically),
                            color = TripRecordPalette.current.primary,
                        )
                    }
                    Text("보내기", color = TripRecordPalette.current.primary)
                }
            }
        },
        dismissButton = {
            if (!submitted) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text("취소", color = TripRecordPalette.current.secondaryText)
                }
            }
        },
    )
}

private const val MaxServiceFeedbackLength = 2000
