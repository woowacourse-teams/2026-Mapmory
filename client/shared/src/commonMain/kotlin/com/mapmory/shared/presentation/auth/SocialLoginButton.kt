package com.mapmory.shared.presentation.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.LocalMapmoryTheme
import mapmoryclient.shared.generated.resources.*
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

/** Brand artwork and source guidelines are documented in docs/social-login-branding.md. */
@Composable
internal fun SocialLoginButton(
    provider: SocialProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dark = LocalMapmoryTheme.current.isDark
    val background = when (provider) {
        SocialProvider.GOOGLE -> Color.White
        SocialProvider.KAKAO -> Color(0xFFFEE500)
        SocialProvider.APPLE -> if (dark) Color.White else Color.Black
    }
    val foreground = when (provider) {
        SocialProvider.GOOGLE -> Color(0xFF1F1F1F)
        SocialProvider.KAKAO -> Color.Black.copy(alpha = 0.85f)
        SocialProvider.APPLE -> if (dark) Color.Black else Color.White
    }
    // Apple artwork includes its own padding. Scale the whole canvas with the title,
    // retaining the official 31:44 ratio and title-to-height proportion.
    val appleHeight = 48.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        border = if (provider == SocialProvider.GOOGLE) BorderStroke(1.dp, Color(0xFF747775)) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = foreground,
            disabledContainerColor = background,
            disabledContentColor = foreground.copy(alpha = 0.5f),
        ),
        contentPadding = if (provider == SocialProvider.APPLE) PaddingValues(horizontal = 24.dp)
            else PaddingValues(horizontal = if (provider == SocialProvider.GOOGLE && !usesIosSignInSpacing()) 12.dp else 16.dp, vertical = 12.dp),
    ) {
        when (provider) {
            SocialProvider.GOOGLE -> {
                Image(painterResource(Res.drawable.google_sign_in_g), contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(if (usesIosSignInSpacing()) 12.dp else 10.dp))
                Text("Google로 계속하기", style = TextStyle(
                    fontFamily = FontFamily(Font(Res.font.google_sans_medium, FontWeight.Medium)),
                    fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                ))
            }
            SocialProvider.KAKAO -> {
                Image(painterResource(Res.drawable.kakao_sign_in_symbol), contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("카카오 로그인", style = TextStyle(
                    fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                ))
            }
            SocialProvider.APPLE -> {
                Image(painterResource(if (dark) Res.drawable.apple_sign_in_black else Res.drawable.apple_sign_in_white),
                    contentDescription = null, modifier = Modifier.height(appleHeight).width(appleHeight * (31f / 44f)))
                Text("Apple로 로그인", style = TextStyle(
                    fontFamily = FontFamily.SansSerif, fontSize = 20.64.sp,
                    textAlign = TextAlign.Center,
                ))
            }
        }
    }
}

internal expect fun usesIosSignInSpacing(): Boolean
