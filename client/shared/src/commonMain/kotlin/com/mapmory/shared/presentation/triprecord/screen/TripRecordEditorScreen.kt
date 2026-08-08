package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState

@Composable
fun TripRecordEditorScreen(
    uiState: TripRecordEditorUiState,
    locations: List<Location>,
    onProvinceChanged: () -> Unit,
    onLocationSelected: (Location) -> Unit,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onStartDateChanged: (String) -> Unit,
    onEndDateChanged: (String) -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    onMapClick: () -> Unit = {},
    onRecordClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val provinces = locations.filter {
        it.type == LocationType.PROVINCE && it.countryId == KoreaCountryId
    }
    val selectedMapCountry = uiState.selectedLocation?.takeIf {
        it.type == LocationType.PROVINCE && it.countryId != KoreaCountryId
    }
    var selectedProvinceId by remember(uiState.selectedLocation?.id, uiState.selectedLocation?.parentId) {
        mutableStateOf(
            when (uiState.selectedLocation?.type) {
                LocationType.PROVINCE -> uiState.selectedLocation.id
                LocationType.DISTRICT -> uiState.selectedLocation.parentId
                null -> provinces.firstOrNull()?.id
            },
        )
    }

    TripRecordBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            TripRecordTopBar(
                title = if (uiState.recordId == null) "새 기록 작성" else "기록 수정",
                onBackClick = onBackClick,
                trailing = {
                    TextButton(
                        onClick = onSaveClick,
                        enabled = !uiState.isSaving,
                    ) {
                        Text(
                            text = if (uiState.isSaving) "저장 중" else "완료",
                            color = if (uiState.isSaving) TripRecordPalette.muted else TripRecordPalette.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    EditorSectionLabel("제목")
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = onTitleChanged,
                        placeholder = { Text("여행의 제목을 입력해 주세요") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }

                item {
                    EditorSectionLabel("여행 사진")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TripPhotoPlaceholder(Modifier.size(112.dp, 84.dp), variant = 0)
                        TripPhotoPlaceholder(Modifier.size(112.dp, 84.dp), variant = 1)
                        TripPhotoPlaceholder(Modifier.size(112.dp, 84.dp), variant = 2)
                        Column(
                            modifier = Modifier
                                .size(84.dp)
                                .background(TripRecordPalette.surface, RoundedCornerShape(16.dp)),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        ) {
                            Text("＋", color = TripRecordPalette.accent, fontSize = 28.sp)
                            Text("사진 추가", color = TripRecordPalette.muted, fontSize = 10.sp)
                        }
                    }
                }

                item {
                    EditorSectionLabel("장소")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        selectedMapCountry?.let { country ->
                            EditorChoiceChip(
                                text = country.name,
                                selected = true,
                                onClick = {},
                            )
                        }
                        provinces.forEach { province ->
                            EditorChoiceChip(
                                text = province.name,
                                selected = selectedProvinceId == province.id,
                                onClick = {
                                    selectedProvinceId = province.id
                                    onProvinceChanged()
                                },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        locations
                            .filter { it.type == LocationType.DISTRICT && it.parentId == selectedProvinceId }
                            .forEach { district ->
                                EditorChoiceChip(
                                    text = district.name,
                                    selected = uiState.selectedLocation?.id == district.id,
                                    onClick = { onLocationSelected(district) },
                                )
                            }
                    }
                }

                item {
                    EditorSectionLabel("여행 날짜")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.startDate,
                            onValueChange = onStartDateChanged,
                            placeholder = { Text("YYYY.MM.DD") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = uiState.endDate,
                            onValueChange = onEndDateChanged,
                            placeholder = { Text("종료일") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    EditorSectionLabel("기록")
                    OutlinedTextField(
                        value = uiState.content,
                        onValueChange = onContentChanged,
                        placeholder = { Text("여행에서 느낀 점을 기록해 보세요") },
                        minLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }

                uiState.errorMessage?.let { message ->
                    item {
                        Text(message, color = TripRecordPalette.danger, fontSize = 12.sp)
                    }
                }

                item { Spacer(Modifier.height(18.dp)) }
            }

            TripBottomBar(
                selected = TripBottomTab.CREATE,
                onMapClick = onMapClick,
                onRecordClick = onRecordClick,
                onProfileClick = onProfileClick,
            )
        }
    }
}
@Composable
private fun EditorSectionLabel(text: String) {
    Text(
        text = text,
        color = TripRecordPalette.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EditorChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (selected) TripRecordPalette.background else TripRecordPalette.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (selected) TripRecordPalette.accent else TripRecordPalette.surface,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 13.dp, vertical = 9.dp),
    )
}


private const val KoreaCountryId = 1L
