package com.mapmory.shared.presentation.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.presentation.components.MapmoryAsyncImage
import com.mapmory.shared.presentation.triprecord.screen.TripRecordCardLayout

/** Identifies records by photo, region and dates using the journal's card layout. */
@Composable
internal fun AlbumRecordPicker(
    records: List<TripRecordSummary>, loading: Boolean, error: String?, hasMore: Boolean,
    enabled: Boolean, status: (TripRecordSummary) -> String?, onBack: () -> Unit,
    onSelect: (TripRecordSummary) -> Unit, onMore: () -> Unit, onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Text("내 기록 불러오기", style = MaterialTheme.typography.titleLarge)
        }
        Text("앨범에 담을 기록을 선택해 주세요.", Modifier.padding(vertical = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (records.isEmpty() && !loading && error == null) item { Text("아직 저장된 기록이 없어요. 새 기록을 추가해 보세요.") }
            items(records, key = { it.id }) { record ->
                val label = status(record)
                val selectable = enabled && label == null
                TripRecordCardLayout(
                    modifier = Modifier.alpha(if (selectable) 1f else 0.55f).clickable(enabled = selectable) { onSelect(record) },
                    photo = {
                        MapmoryAsyncImage(imageBytes = record.thumbnailPreviewBytes, imageUri = record.thumbnailUrl,
                            contentDescription = record.regionName ?: "여행 사진", modifier = Modifier.fillMaxSize())
                    },
                    details = {
                        Text(record.regionName?.takeIf { it.isNotBlank() } ?: "여행 기록", fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(record.startDate.takeIf { it.isNotBlank() }, record.endDate?.takeIf { it.isNotBlank() })
                            .distinct().joinToString(" – ").replace('-', '.'), style = MaterialTheme.typography.bodySmall)
                        record.photoCount?.let { Text("사진 ${it}장", style = MaterialTheme.typography.bodySmall) }
                        label?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    },
                )
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (error != null) item {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, enabled = !loading) { Text("다시 시도") }
            }
            if (hasMore) item { TextButton(onClick = onMore, enabled = enabled) { Text("기록 더 보기") } }
        }
    }
}
