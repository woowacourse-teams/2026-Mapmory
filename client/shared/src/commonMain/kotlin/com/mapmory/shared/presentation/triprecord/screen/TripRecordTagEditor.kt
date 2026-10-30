package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState

/** 현재 수정 폼에서는 숨기지만, 태그 입력 UI를 다시 사용할 수 있도록 독립 보관한다. */
@Composable
internal fun TripRecordTagEditor(
    uiState: TripRecordEditorUiState,
    saveErrorMessage: String?,
    onInputChanged: (String) -> Unit,
    onTagToggled: (Long) -> Unit,
    onPendingTagToggled: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "태그 (선택)",
                color = TripRecordPalette.current.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${uiState.selectedTagCount}/5",
                color = TripRecordPalette.current.muted,
                fontSize = 11.sp,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = uiState.tagInput,
                onValueChange = onInputChanged,
                placeholder = { Text("직접 입력 (# 제외)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TripRecordPalette.current.text,
                    unfocusedTextColor = TripRecordPalette.current.text,
                    cursorColor = TripRecordPalette.current.accent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = Color.Transparent,
                    focusedPlaceholderColor = TripRecordPalette.current.muted,
                    unfocusedPlaceholderColor = TripRecordPalette.current.muted,
                ),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onCreate,
                enabled = uiState.tagInput.isNotBlank() && !uiState.isSaving,
            ) {
                Text("추가", color = TripRecordPalette.current.accent)
            }
        }

        if (uiState.availableTags.isNotEmpty() || uiState.pendingTagNames.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.availableTags.forEach { tag ->
                    TripTagChip(
                        text = tag.name,
                        selected = tag.id in uiState.selectedTagIds,
                        onClick = { onTagToggled(tag.id) },
                    )
                }
                uiState.pendingTagNames.forEach { name ->
                    TripTagChip(
                        text = name,
                        selected = name in uiState.selectedPendingTagNames,
                        onClick = { onPendingTagToggled(name) },
                    )
                }
            }
        } else if (!uiState.isTagsLoading) {
            Text(
                text = "아직 태그가 없어요. 원하는 태그를 직접 만들어 보세요.",
                color = TripRecordPalette.current.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        (uiState.tagErrorMessage ?: saveErrorMessage)?.let { message ->
            Text(
                text = message,
                color = TripRecordPalette.current.danger,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
