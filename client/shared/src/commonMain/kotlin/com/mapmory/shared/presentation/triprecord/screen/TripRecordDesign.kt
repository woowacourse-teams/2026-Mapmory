package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.map.ui.KoreaMapArtwork
import org.jetbrains.compose.resources.decodeToImageBitmap

internal object TripRecordPalette {
    val background = Color(0xFF111518)
    val surface = Color(0xFF1A1E22)
    val surfaceElevated = Color(0xFF102A32)
    val line = Color(0xFF1B363E)
    val text = Color(0xFFE9F4F2)
    val muted = Color(0xFF81999E)
    val accent = Color(0xFF35C988)
    val accentSoft = Color(0xFF123E3A)
    val danger = Color(0xFFFF6264)
    val photoRecommendText = Color.White
    val photoRecommendBackground = Color(0xFF382125)
    val photoRecommendBorder = Color(0xFF99555D)
    val photoGalleryBackground = Color(0xFF1B2D26)
    val photoGalleryBorder = Color(0xFF3E7960)

    // Shared tokens used by the map, journal, and statistics dashboards.
    val pageBackground = Color(0xFF121518)
    val softSurface = Color(0xFF1C2124)
    val border = Color(0xFF2B3135)
    val primary = Color(0xFF35C987)
    val primarySoft = Color(0xFF173B2D)
    val secondaryAccent = Color(0xFF67D9A2)
    val onPrimary = Color(0xFF071B12)
    val headingText = Color(0xFFF1F5F3)
    val bodyText = Color(0xFFBDC6C2)
    val secondaryText = Color(0xFF89938F)
    val navigationDivider = Color(0xFF2C3431)
    val navigationUnselected = Color(0xFF77827D)
    val navigationSelectedLabel = Color(0xFFA2ADA7)
    val contentOnMedia = Color.White
    val mediaScrim = Color.Black.copy(alpha = 0.62f)
    val metadataDateBackground = Color(0xFF24292D)
}

internal object TripMapPalette {
    val logoText = Color(0xFFF4F8F5)
    val scopeBackground = Color(0xFF151C19)
    val scopeBorder = Color(0xFF2D3A34)
    val scopeSelectedBackground = Color(0xFF2A3832)
    val scopeSelectedText = Color(0xFFEEF7F1)
    val scopeUnselectedText = Color(0xFF92A09A)
    val tagBackground = Color(0xFF1A2421)
    val tagText = Color(0xFFBDC8C2)
    val tagSelectedText = Color(0xFF072118)
    val dashboardBadgeText = Color(0xFF9CE6BF)
}

internal object TripStatisticsPalette {
    val divider = Color(0xFF30363A)
    val mapBorder = Color(0xFF343B40)
    val mapLand = Color(0xFF293039)
    val mapOutline = Color(0xFF424B53)
}

@Composable
internal fun rememberDismissKeyboardOnTapModifier(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    return Modifier.pointerInput(focusManager, keyboardController) {
        detectTapGestures {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
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
    backgroundColor: Color = TripRecordPalette.background,
    content: @Composable () -> Unit,
) {
    TripRecordTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor,
        ) {
            Box(
                modifier = modifier.fillMaxSize(),
            ) {
                content()
            }
        }
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
                contentDescription = "뒤로가기",
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
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = TripRecordPalette.surface,
    contentColor: Color = TripRecordPalette.text,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
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
    backgroundColor: Color = TripRecordPalette.background,
    dividerColor: Color = TripRecordPalette.line,
    selectedIconColor: Color = TripRecordPalette.accent,
    selectedLabelColor: Color = TripRecordPalette.accent,
    unselectedColor: Color = TripRecordPalette.muted,
    contentTopPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(backgroundColor)
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TripBottomItem(
            tab = TripBottomTab.MAP,
            selected = selected == TripBottomTab.MAP,
            onClick = onMapClick,
            selectedIconColor = selectedIconColor,
            selectedLabelColor = selectedLabelColor,
            unselectedColor = unselectedColor,
            contentTopPadding = contentTopPadding,
            modifier = Modifier.weight(1f),
        )
        TripBottomItem(
            tab = TripBottomTab.RECORD,
            selected = selected == TripBottomTab.RECORD,
            onClick = onRecordClick,
            selectedIconColor = selectedIconColor,
            selectedLabelColor = selectedLabelColor,
            unselectedColor = unselectedColor,
            contentTopPadding = contentTopPadding,
            modifier = Modifier.weight(1f),
        )
        TripBottomItem(
            tab = TripBottomTab.PROFILE,
            selected = selected == TripBottomTab.PROFILE,
            onClick = onProfileClick,
            selectedIconColor = selectedIconColor,
            selectedLabelColor = selectedLabelColor,
            unselectedColor = unselectedColor,
            contentTopPadding = contentTopPadding,
            modifier = Modifier.weight(1f),
        )
    }
}

internal enum class TripBottomTab(val label: String) {
    MAP("지도"),
    RECORD("일지"),
    PROFILE("통계"),
}

@Composable
private fun TripBottomItem(
    tab: TripBottomTab,
    selected: Boolean,
    onClick: () -> Unit,
    selectedIconColor: Color,
    selectedLabelColor: Color,
    unselectedColor: Color,
    contentTopPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val iconColor = if (selected) selectedIconColor else unselectedColor
    val labelColor = if (selected) selectedLabelColor else unselectedColor
    Column(
        modifier = modifier
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(top = contentTopPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TripBottomIcon(
            tab = tab,
            color = iconColor,
            modifier = Modifier.size(25.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = tab.label,
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TripBottomIcon(
    tab: TripBottomTab,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val stroke = Stroke(
            width = 1.7.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (tab) {
            TripBottomTab.MAP -> {
                val mapPath = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.23f)
                    lineTo(size.width * 0.36f, size.height * 0.10f)
                    lineTo(size.width * 0.66f, size.height * 0.23f)
                    lineTo(size.width * 0.90f, size.height * 0.10f)
                    lineTo(size.width * 0.90f, size.height * 0.77f)
                    lineTo(size.width * 0.66f, size.height * 0.90f)
                    lineTo(size.width * 0.36f, size.height * 0.77f)
                    lineTo(size.width * 0.12f, size.height * 0.90f)
                    close()
                }
                drawPath(mapPath, color = color, style = stroke)
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.36f, size.height * 0.10f),
                    end = Offset(size.width * 0.36f, size.height * 0.77f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.66f, size.height * 0.23f),
                    end = Offset(size.width * 0.66f, size.height * 0.90f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }

            TripBottomTab.RECORD -> {
                listOf(0.18f, 0.47f, 0.76f).forEach { y ->
                    drawRect(
                        color = color,
                        topLeft = Offset(size.width * 0.12f, size.height * y),
                        size = Size(size.width * 0.17f, size.height * 0.17f),
                        style = stroke,
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.43f, size.height * (y + 0.085f)),
                        end = Offset(size.width * 0.90f, size.height * (y + 0.085f)),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            }

            TripBottomTab.PROFILE -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.08f, size.height * 0.88f),
                    end = Offset(size.width * 0.92f, size.height * 0.88f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                listOf(
                    0.24f to 0.50f,
                    0.50f to 0.16f,
                    0.76f to 0.34f,
                ).forEach { (x, top) ->
                    drawLine(
                        color = color,
                        start = Offset(size.width * x, size.height * top),
                        end = Offset(size.width * x, size.height * 0.88f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TripPhotoPlaceholder(
    modifier: Modifier = Modifier,
    variant: Int = 0,
    shape: Shape = RoundedCornerShape(18.dp),
) {
    val skyColors = when (variant % 3) {
        0 -> listOf(Color(0xFFEEA16C), Color(0xFFE56A66), Color(0xFF305C6B))
        1 -> listOf(Color(0xFFB5C991), Color(0xFF5A896F), Color(0xFF253E4A))
        else -> listOf(Color(0xFFB88F85), Color(0xFF596D8E), Color(0xFF203846))
    }
    Box(
        modifier = modifier
            .clip(shape)
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
internal fun TripPhotoImage(
    imageBytes: ByteArray?,
    fallbackBytes: ByteArray? = null,
    contentDescription: String,
    modifier: Modifier = Modifier,
    placeholderVariant: Int = 0,
    shape: Shape = RoundedCornerShape(18.dp),
) {
    val bitmap = remember(imageBytes, fallbackBytes) {
        imageBytes.decodeToImageBitmapOrNull()
            ?: fallbackBytes.decodeToImageBitmapOrNull()
    }
    if (bitmap == null) {
        TripPhotoPlaceholder(modifier, placeholderVariant, shape)
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    }
}

private fun ByteArray?.decodeToImageBitmapOrNull() =
    this?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }

@Composable
fun TripMapArtwork(
    modifier: Modifier = Modifier,
) {
    KoreaMapArtwork(modifier = modifier)
}
