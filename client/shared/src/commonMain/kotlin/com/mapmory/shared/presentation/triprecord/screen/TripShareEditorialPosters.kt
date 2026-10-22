package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState

private val EditorialPosterPaper = Color(0xFFF8F7F3)
private val EditorialPosterInk = Color(0xFF21342D)
private val EditorialPosterMuted = Color(0xFF65736B)
private val EditorialPosterAccent = Color(0xFF4D9276)

@Composable
internal fun DestinationEditionPoster(
    record: TripRecordItemUiState,
    selectedPhoto: TripRecordPhotoUiState?,
    modifier: Modifier,
) {
    PosterFrame(modifier) {
        Column(Modifier.fillMaxSize().background(EditorialPosterPaper)) {
            MomentPhoto(
                record,
                selectedPhoto ?: record.photos.minByOrNull { it.sortOrder },
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
internal fun PostcardPoster(
    record: TripRecordItemUiState,
    selectedPhoto: TripRecordPhotoUiState?,
    modifier: Modifier,
) {
    PosterFrame(modifier) {
        Column(Modifier.fillMaxSize().background(EditorialPosterPaper).padding(24.dp)) {
            Spacer(Modifier.height(8.dp))
            MomentPhoto(
                record,
                selectedPhoto ?: record.photos.minByOrNull { it.sortOrder },
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
private fun MomentPhoto(
    record: TripRecordItemUiState,
    photo: TripRecordPhotoUiState?,
    modifier: Modifier,
) {
    if (photo == null) PosterPhotoPlaceholder(modifier, "사진 없이 남긴 기록")
    else SharePosterPhoto(photo, modifier, "${record.locationName} 여행 사진")
}
