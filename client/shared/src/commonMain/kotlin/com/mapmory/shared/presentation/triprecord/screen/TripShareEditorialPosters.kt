package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState

private val SharePaper = Color(0xFFF8F6EE)
private val ShareInk = Color(0xFF243C2D)
private val ShareMuted = Color(0xFF7C897A)
private val ShareRule = Color(0xFFC6CFC1)

private val EditorialPosterPaper = Color(0xFFF8F7F3)
private val EditorialPosterInk = Color(0xFF21342D)
private val EditorialPosterMuted = Color(0xFF65736B)
private val EditorialPosterAccent = Color(0xFF4D9276)

@Composable
internal fun DestinationEditionPoster(record: TripRecordItemUiState, modifier: Modifier) {
    PosterFrame(modifier) {
        Column(Modifier.fillMaxSize().background(EditorialPosterPaper)) {
            MomentPhoto(
                record,
                record.photos.minByOrNull { it.sortOrder },
                Modifier.fillMaxWidth().weight(0.72f),
            )
            Column(
                Modifier.fillMaxWidth().weight(0.28f).padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    record.locationName,
                    color = EditorialPosterInk,
                    fontSize = 30.sp,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.8).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        recordDateRange(record),
                        color = EditorialPosterMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Text("mapmory", color = EditorialPosterAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun ContactSheetPoster(records: List<TripRecordItemUiState>, modifier: Modifier) {
    if (records.size == 1) {
        SingleRecordContactSheet(records.single(), modifier)
        return
    }
    val moments = posterMoments(records, 4)
    PosterFrame(modifier) {
        Column(Modifier.fillMaxSize().background(SharePaper).padding(22.dp)) {
            PosterMasthead("MY PHOTO ARCHIVE", ShareInk)
            Row(Modifier.fillMaxWidth().height(87.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (records.size == 1) "다시 꺼내 본,\n여행의 장면." else "다녀온 곳마다,\n남겨 둔 한 장.", color = ShareInk,
                    fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 28.sp, letterSpacing = (-1).sp)
                Text(moments.size.toString().padStart(2, '0'), color = Color(0xFF839783), fontFamily = FontFamily.Serif, fontSize = 47.sp, lineHeight = 50.sp)
            }
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val rows = if (moments.size <= 2) moments.map { listOf(it) } else moments.chunked(2)
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { moment ->
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                MomentPhoto(moment.record, moment.photo, Modifier.fillMaxWidth().weight(1f))
                                Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(moment.record.locationName, color = ShareInk, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(moment.date, color = ShareMuted, fontSize = 7.sp, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(0.5.dp).background(ShareRule))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("기록 ${records.size}개 / 사진 ${moments.count { it.photo != null }}장", color = ShareInk, fontSize = 9.sp)
                Text("KEEP YOUR PLACES.", color = ShareInk, fontSize = 7.sp, letterSpacing = 0.6.sp)
            }
        }
    }
}

@Composable
private fun SingleRecordContactSheet(record: TripRecordItemUiState, modifier: Modifier) {
    val moments = posterMoments(listOf(record), 4)
    PosterFrame(modifier) {
        Column(Modifier.fillMaxSize().background(EditorialPosterPaper).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("mapmory", color = EditorialPosterAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("사진 기록", color = EditorialPosterMuted, fontSize = 9.sp)
            }
            Text(record.locationName, color = EditorialPosterInk, fontSize = 28.sp, lineHeight = 33.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 19.dp, bottom = 6.dp))
            Text(recordDateRange(record), color = EditorialPosterMuted, fontSize = 9.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                moments.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { columnIndex, moment ->
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                MomentPhoto(record, moment.photo, Modifier.fillMaxWidth().weight(1f))
                                if (moment.photo != null) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text((rowIndex * 2 + columnIndex + 1).toString().padStart(2, '0'),
                                        color = EditorialPosterInk, fontSize = 8.sp)
                                    Text(moment.date, color = EditorialPosterMuted, fontSize = 8.sp, maxLines = 1)
                                }
                            }
                        }
                        if (row.size == 1 && moments.size > 2) Spacer(Modifier.weight(1f))
                    }
                }
            }
            val total = record.photoCount ?: record.photos.size
            val shown = moments.count { it.photo != null }
            Text(if (total > shown) "사진 ${total}장 중 ${shown}장" else "사진 ${total}장",
                color = EditorialPosterMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
internal fun PostcardPoster(record: TripRecordItemUiState, modifier: Modifier) {
    PosterFrame(modifier) {
        Column(Modifier.fillMaxSize().background(EditorialPosterPaper).padding(24.dp)) {
            Spacer(Modifier.height(8.dp))
            MomentPhoto(
                record,
                record.photos.minByOrNull { it.sortOrder },
                Modifier.fillMaxWidth().weight(1f),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        record.locationName,
                        color = EditorialPosterInk,
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        letterSpacing = (-0.5).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        recordDateRange(record),
                        color = EditorialPosterMuted,
                        fontSize = 9.sp,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text("mapmory", color = EditorialPosterAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PosterMasthead(caption: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("MAPMORY", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(caption, color = color, fontSize = 7.sp, letterSpacing = 0.5.sp)
    }
}

private data class PosterMoment(val record: TripRecordItemUiState, val photo: TripRecordPhotoUiState?) {
    val date: String get() = (photo?.capturedAt?.take(10) ?: record.startDate).orEmpty().replace('-', '.')
}

private fun posterMoments(records: List<TripRecordItemUiState>, limit: Int): List<PosterMoment> {
    val moments = if (records.size == 1) {
        records.single().let { record -> record.photos.sortedBy { it.sortOrder }.take(limit).map { PosterMoment(record, it) } }
    } else {
        records.map { record -> PosterMoment(record, record.photos.minByOrNull { it.sortOrder }) }.take(limit)
    }
    return moments.ifEmpty { records.take(1).map { PosterMoment(it, null) } }
}

@Composable
private fun MomentPhoto(
    record: TripRecordItemUiState,
    photo: TripRecordPhotoUiState?,
    modifier: Modifier,
) {
    if (photo == null) PosterPhotoPlaceholder(modifier, "사진 없이 남긴 기록")
    else SharePosterPhoto(photo, modifier, "${record.locationName} 여행 사진")
}
