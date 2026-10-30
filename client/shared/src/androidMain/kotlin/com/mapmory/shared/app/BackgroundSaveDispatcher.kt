package com.mapmory.shared.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun backgroundSaveDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
