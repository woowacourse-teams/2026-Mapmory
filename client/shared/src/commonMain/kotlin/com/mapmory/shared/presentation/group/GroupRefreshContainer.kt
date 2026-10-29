package com.mapmory.shared.presentation.group

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupRefreshContainer(
    refreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    Box(modifier.pullToRefresh(isRefreshing = refreshing, state = state, enabled = enabled, onRefresh = onRefresh)) {
        content()
        if (enabled || refreshing) PullToRefreshDefaults.Indicator(state, refreshing, Modifier.align(Alignment.TopCenter))
    }
}
