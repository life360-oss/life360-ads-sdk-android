package com.life360.demo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Crop169
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.life360.ads.AdSize

// The Nativo bid request sends `tagid = configId` to a fixed exchange endpoint, so the creative
// type a slot receives is decided server-side by the placement — not by anything the app requests.
// Point each of these at the placement configured for that format.
// TODO: replace with the real Nativo imp/placement ids.
private const val BANNER_CONFIG_ID = "nativo-imp-id"
private const val NATIVE_CONFIG_ID = "nativo-imp-id"
private const val VIDEO_CONFIG_ID = "nativo-imp-id"

/**
 * The three demand shapes this harness exercises. All of them render through `BannerView`, which is
 * the only in-feed path the SDK instruments with Open Measurement — the interstitial and rewarded
 * units can't be scrolled, so they'd tell us nothing about viewability.
 *
 * [slotHeight] is a fixed reservation rather than wrap-content on purpose: Nativo's renderer expands
 * a creative to fill its container when the container is taller than the bid's minimum, so a
 * definite height makes the rendered size predictable instead of dependent on measure order.
 */
enum class DemoAdFormat(
    val title: String,
    val icon: ImageVector,
    val configId: String,
    val adSize: AdSize,
    val slotHeight: Dp,
) {
    BANNER("Banner", Icons.Outlined.Crop169, BANNER_CONFIG_ID, AdSize(320, 50), 100.dp),
    VIDEO("L360 Video", Icons.Outlined.SmartDisplay, VIDEO_CONFIG_ID, AdSize(320, 180), 100.dp),
    NATIVE("Native", Icons.Outlined.Article, NATIVE_CONFIG_ID, AdSize(320, 250), 100.dp),
}
