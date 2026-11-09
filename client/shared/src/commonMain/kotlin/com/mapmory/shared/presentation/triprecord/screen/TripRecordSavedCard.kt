package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.app.BackgroundTripRecordCompletion
import com.mapmory.shared.presentation.components.MapmoryAsyncImage

/** A non-modal receipt: the user stays where they are until explicitly choosing View. */
@Composable
internal fun TripRecordSavedCard(
    completion: BackgroundTripRecordCompletion,
    onView: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = TripRecordPalette.current
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MapmoryAsyncImage(
                imageBytes = completion.thumbnailBytes,
                imageUri = if (completion.thumbnailBytes == null) completion.thumbnailUrl else null,
                contentDescription = "저장한 여행 사진",
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = completion.locationName.takeIf { it.isNotBlank() }?.let { "$it 기록이 저장됐어요." }
                        ?: "기록이 저장됐어요.",
                    color = colors.headingText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("사진 ${completion.photoCount}장", color = colors.bodyText, fontSize = 12.sp)
            }
            if (onView != null) {
                TextButton(
                    onClick = onView,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = colors.primarySoft,
                        contentColor = colors.headingText,
                    ),
                ) {
                    Text("보기", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
