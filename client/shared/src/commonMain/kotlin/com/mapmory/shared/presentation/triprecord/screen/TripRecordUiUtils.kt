package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

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
