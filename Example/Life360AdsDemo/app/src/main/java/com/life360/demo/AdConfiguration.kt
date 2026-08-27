package com.life360.demo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Crop169
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The three demand shapes this harness exercises, matching the iOS counterpart's tabs.
 *
 * Each format's request shape — config id, size, `ntv_*` targeting overrides — lives directly on
 * its own [AdSlotController] rather than here, so it's visible in the same class that uses it. See
 * [BannerAdSlotController], [Life360VideoAdSlotController], and [NativeAdSlotController].
 *
 * [slotHeight] is a fixed reservation rather than wrap-content on purpose: Nativo's renderer expands
 * a creative to fill its container when the container is taller than the bid's minimum, so a
 * definite height makes the rendered size predictable instead of dependent on measure order. Native's
 * card has no such minimum, so its height is just a generous estimate of the bound content.
 */
enum class AdConfiguration(
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
