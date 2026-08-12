package com.life360.ads.exposure

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.LEGACY

/**
 * Covers [Life360FriendlyObstructionManager]: the point-in-time detection ([friendlyObstructionViews])
 * that decides which on-top views OMID should treat as friendly obstructions, and the stateful
 * [reconcile] that emits only the delta as the ad scrolls under / out from overlays. Detection
 * mistakes either erode viewability (transparent overlays counted) or hide real occluders; delta
 * mistakes either re-register the same view every tick or leak stale registrations.
 *
 * Geometry note: views are laid out only *after* the whole hierarchy is attached — an `addView`
 * (which calls `requestLayout`) after a manual `layout()` would re-expand earlier views to
 * `MATCH_PARENT`, clobbering their coordinates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "w800dp-h800dp-xhdpi")
@LooperMode(LEGACY)
class Life360FriendlyObstructionManagerTest {

    private val adRect = intArrayOf(0, 0, 200, 200)

    private lateinit var activity: Activity
    private lateinit var container: FrameLayout
    private lateinit var ad: View
    private val manager = Life360FriendlyObstructionManager()

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java)
            .setup().create().visible().resume().windowFocusChanged(true).get()
        container = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        activity.setContentView(container)
        ad = View(activity)
        container.addView(ad)
    }

    /** Lays out the ad at [adRect] plus each (view -> rect) pair — call last, after all `addView`s. */
    private fun layoutFull(vararg placements: Pair<View, IntArray>) {
        ad.layout(adRect[0], adRect[1], adRect[2], adRect[3])
        placements.forEach { (view, r) -> if (view.parent != null) view.layout(r[0], r[1], r[2], r[3]) }
    }

    private fun rect(left: Int = 0, top: Int = 0, right: Int = 200, bottom: Int = 200) =
        intArrayOf(left, top, right, bottom)

    private fun opaqueBackground() = ColorDrawable(Color.RED)

    private fun detect() = manager.friendlyObstructionViews(ad)

    // region Detection

    @Test
    fun overlappingTransparentSibling_isRegistered() {
        val overlay = View(activity)
        container.addView(overlay)
        layoutFull(overlay to rect())

        assertEquals(listOf(overlay), detect())
    }

    @Test
    fun transparentContainerWrappingOnlyTransparentChildren_registersContainerAsSingleRoot() {
        val overlay = FrameLayout(activity)
        val childA = View(activity)
        val childB = View(activity)
        overlay.addView(childA)
        overlay.addView(childB)
        container.addView(overlay)
        layoutFull(overlay to rect(), childA to rect(), childB to rect())

        assertEquals(listOf<View>(overlay), detect())
    }

    @Test
    fun transparentChildOverOpaqueSiblingInsideContainer_registersOnlyTransparentChild() {
        val overlay = FrameLayout(activity)
        val opaque = View(activity).apply { background = opaqueBackground() }
        val transparent = View(activity)
        overlay.addView(opaque)
        overlay.addView(transparent)
        container.addView(overlay)
        layoutFull(overlay to rect(), opaque to rect(), transparent to rect())

        val result = detect()
        assertEquals(listOf(transparent), result)
        assertFalse(result.contains(overlay))
        assertFalse(result.contains(opaque))
    }

    @Test
    fun transparentContainerWithOpaqueChildOutsideAdFrame_isRegistered() {
        val overlay = FrameLayout(activity)
        val opaqueOutside = View(activity).apply { background = opaqueBackground() }
        overlay.addView(opaqueOutside)
        container.addView(overlay)
        layoutFull(
            overlay to rect(0, 0, 600, 600),
            opaqueOutside to rect(400, 400, 600, 600)
        )

        assertEquals(listOf<View>(overlay), detect())
    }

    @Test
    fun overlappingOpaqueBackgroundSibling_notRegistered() {
        val overlay = View(activity).apply { background = opaqueBackground() }
        container.addView(overlay)
        layoutFull(overlay to rect())

        assertTrue(detect().isEmpty())
    }

    @Test
    fun imageViewWithDrawable_notRegistered() {
        val overlay = ImageView(activity).apply { setImageDrawable(ColorDrawable(Color.BLUE)) }
        container.addView(overlay)
        layoutFull(overlay to rect())

        assertTrue(detect().isEmpty())
    }

    @Test
    fun textViewWithText_notRegistered() {
        val overlay = TextView(activity).apply {
            text = "sponsored"
            setTextColor(Color.BLACK)
        }
        container.addView(overlay)
        layoutFull(overlay to rect())

        assertTrue(detect().isEmpty())
    }

    @Test
    fun webView_notRegistered() {
        val overlay = WebView(activity)
        container.addView(overlay)
        layoutFull(overlay to rect())

        assertTrue(detect().isEmpty())
    }

    @Test
    fun transparentContainerWrappingOpaqueChild_containerNotRegistered() {
        val overlay = FrameLayout(activity)
        val opaqueChild = View(activity).apply { background = opaqueBackground() }
        overlay.addView(opaqueChild)
        container.addView(overlay)
        layoutFull(overlay to rect(), opaqueChild to rect())

        assertTrue(detect().isEmpty())
    }

    @Test
    fun goneSibling_notRegistered() {
        val overlay = View(activity).apply { visibility = View.GONE }
        container.addView(overlay)
        layoutFull(overlay to rect())

        assertTrue(detect().isEmpty())
    }

    @Test
    fun invisibleSibling_notRegistered() {
        val overlay = View(activity).apply { visibility = View.INVISIBLE }
        container.addView(overlay)
        layoutFull(overlay to rect())

        assertTrue(detect().isEmpty())
    }

    @Test
    fun zeroAlphaSibling_notRegistered() {
        val overlay = View(activity).apply { alpha = 0f }
        container.addView(overlay)
        layoutFull(overlay to rect())

        assertTrue(detect().isEmpty())
    }

    @Test
    fun nonOverlappingSibling_notRegistered() {
        val overlay = View(activity)
        container.addView(overlay)
        layoutFull(overlay to rect(left = 400, top = 400, right = 600, bottom = 600))

        assertTrue(detect().isEmpty())
    }

    @Test
    fun transparentSiblingDrawnUnderAd_notRegistered() {
        val under = View(activity)
        container.addView(under, 0)
        layoutFull(under to rect())

        assertTrue(detect().isEmpty())
    }

    @Test
    fun nullAdView_returnsEmpty() {
        assertTrue(manager.friendlyObstructionViews(null).isEmpty())
    }

    @Test
    fun detachedAdView_returnsEmpty() {
        assertTrue(manager.friendlyObstructionViews(View(activity)).isEmpty())
    }

    // endregion

    // region Reconcile (delta tracking)

    @Test
    fun reconcile_overlappingTransparentOverlay_addsOnce_thenNoDelta() {
        val overlay = View(activity)
        container.addView(overlay)
        layoutFull(overlay to rect())

        val first = manager.reconcile(ad)
        assertEquals(listOf(overlay), first.added)
        assertTrue(first.removed.isEmpty())

        val second = manager.reconcile(ad) // nothing changed
        assertTrue(second.added.isEmpty())
        assertTrue(second.removed.isEmpty())
    }

    @Test
    fun reconcile_overlayScrollsOffAd_isRemoved() {
        val overlay = View(activity)
        container.addView(overlay)
        layoutFull(overlay to rect())
        manager.reconcile(ad)

        overlay.layout(400, 400, 600, 600) // no longer overlaps the ad
        val result = manager.reconcile(ad)

        assertTrue(result.added.isEmpty())
        assertEquals(listOf(overlay), result.removed)
    }

    @Test
    fun reconcile_overlayBecomesOpaque_isRemoved() {
        val overlay = View(activity)
        container.addView(overlay)
        layoutFull(overlay to rect())
        manager.reconcile(ad)

        overlay.setBackgroundColor(Color.BLACK) // now paints over the ad
        val result = manager.reconcile(ad)

        assertTrue(result.added.isEmpty())
        assertEquals(listOf(overlay), result.removed)
    }

    @Test
    fun reconcile_overlayDetached_isRemoved() {
        val overlay = View(activity)
        container.addView(overlay)
        layoutFull(overlay to rect())
        manager.reconcile(ad)

        container.removeView(overlay)
        val result = manager.reconcile(ad)

        assertTrue(result.added.isEmpty())
        assertEquals(listOf(overlay), result.removed)
    }

    @Test
    fun reconcile_afterClear_forgetsTracking_soDisappearanceEmitsNoRemove() {
        val overlay = View(activity)
        container.addView(overlay)
        layoutFull(overlay to rect())
        manager.reconcile(ad)

        manager.clear() // e.g. OM session ended

        overlay.layout(400, 400, 600, 600)
        val result = manager.reconcile(ad)

        assertTrue(result.added.isEmpty())
        assertTrue(result.removed.isEmpty())
    }

    // endregion
}
