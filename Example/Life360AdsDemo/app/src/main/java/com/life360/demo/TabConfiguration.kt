package com.life360.demo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Crop169
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


enum class TabConfiguration(
    val title: String,
    val icon: ImageVector,
    val slotHeight: Dp,
) {
    BANNER(
        title = "Banner",
        icon = Icons.Outlined.Crop169,
        slotHeight = 100.dp,
    ),
    VIDEO(
        title = "L360 Video",
        icon = Icons.Outlined.SmartDisplay,
        slotHeight = 250.dp,
    ),
    NATIVE(
        title = "Native",
        icon = Icons.Outlined.Article,
        slotHeight = 340.dp,
    ),
}
