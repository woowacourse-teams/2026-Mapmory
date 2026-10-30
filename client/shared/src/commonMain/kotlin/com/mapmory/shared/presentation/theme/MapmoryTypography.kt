package com.mapmory.shared.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import mapmoryclient.shared.generated.resources.Res
import mapmoryclient.shared.generated.resources.line_seed_kr_bold
import mapmoryclient.shared.generated.resources.line_seed_kr_regular
import org.jetbrains.compose.resources.Font

@Composable
internal fun MapmoryTypography(): Typography {
    val fontFamily = FontFamily(
        Font(Res.font.line_seed_kr_regular, FontWeight.Normal),
        Font(Res.font.line_seed_kr_bold, FontWeight.Bold),
    )

    return remember {
        val defaults = Typography()
        defaults.copy(
            displayLarge = defaults.displayLarge.withFontFamily(fontFamily),
            displayMedium = defaults.displayMedium.withFontFamily(fontFamily),
            displaySmall = defaults.displaySmall.withFontFamily(fontFamily),
            headlineLarge = defaults.headlineLarge.withFontFamily(fontFamily),
            headlineMedium = defaults.headlineMedium.withFontFamily(fontFamily),
            headlineSmall = defaults.headlineSmall.withFontFamily(fontFamily),
            titleLarge = defaults.titleLarge.withFontFamily(fontFamily),
            titleMedium = defaults.titleMedium.withFontFamily(fontFamily),
            titleSmall = defaults.titleSmall.withFontFamily(fontFamily),
            bodyLarge = defaults.bodyLarge.withFontFamily(fontFamily),
            bodyMedium = defaults.bodyMedium.withFontFamily(fontFamily),
            bodySmall = defaults.bodySmall.withFontFamily(fontFamily),
            labelLarge = defaults.labelLarge.withFontFamily(fontFamily),
            labelMedium = defaults.labelMedium.withFontFamily(fontFamily),
            labelSmall = defaults.labelSmall.withFontFamily(fontFamily),
        )
    }
}

private fun TextStyle.withFontFamily(fontFamily: FontFamily): TextStyle =
    copy(fontFamily = fontFamily)
