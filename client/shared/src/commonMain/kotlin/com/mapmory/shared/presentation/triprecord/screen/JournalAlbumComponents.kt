package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared cover treatment for personal journals and group albums. */
@Composable
internal fun JournalAlbumHero(
    title: String,
    subtitle: String,
    height: Dp,
    photo: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        photo()
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
        )))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Square, tightly spaced photo rows with the same sizing in both album screens. */
@Composable
internal fun <T> JournalAlbumPhotoRow(
    photos: List<T>,
    columnCount: Int,
    photoSize: () -> Dp,
    modifier: Modifier = Modifier,
    photo: @Composable (T, Modifier) -> Unit,
) {
    val cell = Modifier.layout { measurable, _ ->
        val side = photoSize().roundToPx().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(side, side))
        layout(side, side) { placeable.placeRelative(0, 0) }
    }
    Row(modifier.fillMaxWidth().clipToBounds(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        photos.forEach { photo(it, cell) }
        repeat((columnCount - photos.size).coerceAtLeast(0)) { Spacer(cell) }
    }
}
