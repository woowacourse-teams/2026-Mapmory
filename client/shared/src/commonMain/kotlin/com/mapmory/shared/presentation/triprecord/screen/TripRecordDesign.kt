package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.map.ui.KoreaMapArtwork

internal object TripRecordPalette {
    val background = Color(0xFF07171B)
    val surface = Color(0xFF0C2026)
    val surfaceElevated = Color(0xFF102A32)
    val line = Color(0xFF1B363E)
    val text = Color(0xFFE9F4F2)
    val muted = Color(0xFF81999E)
    val accent = Color(0xFF19E5A2)
    val accentSoft = Color(0xFF123E3A)
    val danger = Color(0xFFFF6264)
}

@Composable
internal fun TripRecordTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TripRecordPalette.accent,
            onPrimary = TripRecordPalette.background,
            secondary = TripRecordPalette.muted,
            background = TripRecordPalette.background,
            onBackground = TripRecordPalette.text,
            surface = TripRecordPalette.surface,
            onSurface = TripRecordPalette.text,
            surfaceVariant = TripRecordPalette.surfaceElevated,
            onSurfaceVariant = TripRecordPalette.muted,
            outline = TripRecordPalette.line,
            error = TripRecordPalette.danger,
        ),
        content = content,
    )
}

@Composable
internal fun TripRecordBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TripRecordTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = TripRecordPalette.background,
            content = content,
        )
    }
}

@Composable
internal fun TripRecordTopBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBackClick != null) {
            TripIconButton(
                label = "←",
                onClick = onBackClick,
            )
            Spacer(Modifier.width(14.dp))
        } else {
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = title,
            color = TripRecordPalette.text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
internal fun TripIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(TripRecordPalette.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = TripRecordPalette.text,
            fontSize = if (label == "•••") 20.sp else 28.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
internal fun TripSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = TripRecordPalette.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
internal fun TripBottomBar(
    selected: TripBottomTab,
    onRecordClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(TripRecordPalette.background)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TripBottomItem(
            tab = TripBottomTab.MAP,
            selected = selected == TripBottomTab.MAP,
            onClick = onMapClick,
        )
        TripBottomItem(
            tab = TripBottomTab.RECORD,
            selected = selected == TripBottomTab.RECORD,
            onClick = onRecordClick,
        )
        TripBottomItem(
            tab = TripBottomTab.CREATE,
            selected = selected == TripBottomTab.CREATE,
            onClick = onCreateClick,
        )
        TripBottomItem(
            tab = TripBottomTab.PROFILE,
            selected = selected == TripBottomTab.PROFILE,
            onClick = onProfileClick,
        )
    }
}

internal enum class TripBottomTab(
    val icon: String,
    val label: String,
) {
    MAP("⌖", "지도"),
    RECORD("▤", "기록"),
    CREATE("＋", "작성"),
    PROFILE("●", "내 정보"),
}

@Composable
private fun TripBottomItem(
    tab: TripBottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = tab.icon,
            color = if (selected) TripRecordPalette.accent else TripRecordPalette.muted,
            fontSize = if (tab == TripBottomTab.CREATE) 26.sp else 20.sp,
            lineHeight = 22.sp,
        )
        Text(
            text = tab.label,
            color = if (selected) TripRecordPalette.accent else TripRecordPalette.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun TripPhotoPlaceholder(
    modifier: Modifier = Modifier,
    variant: Int = 0,
) {
    val skyColors = when (variant % 3) {
        0 -> listOf(Color(0xFFEEA16C), Color(0xFFE56A66), Color(0xFF305C6B))
        1 -> listOf(Color(0xFFB5C991), Color(0xFF5A896F), Color(0xFF253E4A))
        else -> listOf(Color(0xFFB88F85), Color(0xFF596D8E), Color(0xFF203846))
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(skyColors)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sun = Offset(size.width * (0.72f - variant.coerceAtMost(2) * 0.12f), size.height * 0.28f)
            drawCircle(
                color = Color(0xFFFFE8AB),
                radius = size.minDimension * 0.09f,
                center = sun,
            )

            val backHill = Path().apply {
                moveTo(0f, size.height * 0.68f)
                lineTo(size.width * 0.2f, size.height * 0.47f)
                lineTo(size.width * 0.37f, size.height * 0.65f)
                lineTo(size.width * 0.58f, size.height * 0.42f)
                lineTo(size.width, size.height * 0.67f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(backHill, color = Color(0xFF456A62))

            val frontHill = Path().apply {
                moveTo(0f, size.height * 0.82f)
                lineTo(size.width * 0.32f, size.height * 0.64f)
                lineTo(size.width * 0.53f, size.height * 0.76f)
                lineTo(size.width * 0.77f, size.height * 0.55f)
                lineTo(size.width, size.height * 0.72f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(frontHill, color = Color(0xFF1B3C43))
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(size.width * 0.08f, size.height * 0.8f),
                end = Offset(size.width * 0.88f, size.height * 0.73f),
                strokeWidth = size.minDimension * 0.012f,
            )
        }
    }
}

@Composable
fun TripMapArtwork(
    modifier: Modifier = Modifier,
) {
    KoreaMapArtwork(modifier = modifier)
}
