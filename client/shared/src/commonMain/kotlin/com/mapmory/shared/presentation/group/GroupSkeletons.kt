package com.mapmory.shared.presentation.group

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.mapmory.shared.presentation.triprecord.screen.SkeletonBox

@Composable
internal fun GroupListSkeleton() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp).clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            GroupCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(Modifier.size(88.dp), shape = RectangleShape)
                    Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBox(Modifier.fillMaxWidth(0.7f).height(20.dp))
                        SkeletonBox(Modifier.fillMaxWidth(0.9f).height(14.dp))
                    }
                    Spacer(Modifier.width(24.dp))
                }
            }
        }
    }
}

@Composable
internal fun GroupAlbumSkeleton() {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        repeat(2) {
            GroupCard {
                Column {
                    SkeletonBox(Modifier.fillMaxWidth().height(172.dp), shape = RectangleShape)
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBox(Modifier.fillMaxWidth(0.6f).height(20.dp))
                        SkeletonBox(Modifier.fillMaxWidth(0.4f).height(14.dp))
                        SkeletonBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
                    }
                }
            }
        }
    }
}
