package com.life360.demo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Crop169
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.life360.ads.AdSize

/**
 * The three demand shapes this harness exercises, matching the iOS counterpart's tabs.
 *
 * [configId], [adSize], and [customQueryParams] are the request shape for the two formats that
 * render through [BannerAdSlotController] — Banner and L360 Video. Native has no equivalent: its
 * original-API request has no size, and [NativeAdSlotController] owns its own config id instead. Its
 * entry just leaves those three at their defaults.
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
    val configId: String? = null,
    val adSize: AdSize? = null,
    val customQueryParams: Map<String, String> = emptyMap(),
) {
    // Config id, size, and `ntv_*` targeting overrides copied from the iOS counterpart's
    // BannerAdSlotView and Life360VideoAdSlotView, so each tab requests — and renders — the same ad.
    BANNER(
        title = "Banner",
        icon = Icons.Outlined.Crop169,
        slotHeight = 100.dp,
        configId = "nativo-imp-id",
        adSize = AdSize(320, 50),
        customQueryParams = mapOf("ntv_tm" to "tout"),
    ),
    VIDEO(
        title = "L360 Video",
        icon = Icons.Outlined.SmartDisplay,
        slotHeight = 250.dp,
        configId = "nativo-video-tout-imp-id",
        adSize = AdSize(300, 250),
        customQueryParams = mapOf("ntv_a" to "511808", "ntv_tm" to "tout"),
    ),
    NATIVE(
        title = "Native",
        icon = Icons.Outlined.Article,
        slotHeight = 340.dp,
    ),
}
