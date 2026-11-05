package com.mapmory.shared.presentation.group

import android.os.Build

internal actual fun needsClipboardConfirmation(): Boolean = Build.VERSION.SDK_INT < 33
