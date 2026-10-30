package com.mapmory.shared.presentation.privacy

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PrivacyPolicyContent(
    url: String,
    modifier: Modifier,
) {
    Text("개인정보 처리방침은 모바일 앱에서 확인할 수 있습니다.", modifier = modifier)
}
