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
 * Covers [NativoFriendlyObstructionDetector.friendlyObstructionViews], the transparent-overlay
 * detection that decides which on-top views OMID should treat as friendly obstructions. Getting it
 * wrong either lets transparent overlays erode reported viewability (under-reporting) or hides real
 * opaque occluders from OMID (over-reporting).
 *
 * Geometry note: every view is laid out via [layoutFull] only *after* the whole hierarchy is attached
 * — an `addView` (which calls `requestLayout`) after a manual `layout()` would re-expand earlier views
 * to `MATCH_PARENT`, clobbering their coordinates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "w800dp-h800dp-xhdpi")
@LooperMode(LEGACY)
class NativoFriendlyObstructionDetectorTest {

    // The ad occupies the top-left 200x200; overlays overlap it at the same rect unless stated.
    private val adRect = intArrayOf(0, 0, 200, 200)

    private lateinit var activity: Activity
    private lateinit var container: FrameLayout
    private lateinit var ad: View

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java)
            .setup()
            .create()
            .visible()
            .resume()
            .windowFocusChanged(true)
            .get()
        container = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        activity.setContentView(container)
        ad = View(activity)
        container.addView(ad)
    }

    /** Lays out the ad at [adRect] plus each (view -> rect) pair — call last, after all `addView`s. */
    private fun layoutFull(vararg placements: Pair<View, IntArray>) {
        ad.layout(adRect[0], adRect[1], adRect[2], adRect[3])
        placements.forEach { (view, r) -> view.layout(r[0], r[1], r[2], r[3]) }
    }

    private fun rect(left: Int = 0, top: Int = 0, right: Int = 200, bottom: Int = 200) =
        intArrayOf(left, top, right, bottom)

    private fun opaqueBackground() = ColorDrawable(Color.RED)

    private fun detect() = NativoFriendlyObstructionDetector().friendlyObstructionViews(ad)

    // --- Registered ------------------------------------------------------------------------------

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

        // The whole subtree is transparent, so only the container is the maximal root.
        assertEquals(listOf<View>(overlay), detect())
    }

    @Test
    fun transparentChildOverOpaqueSiblingInsideContainer_registersOnlyTransparentChild() {
        val overlay = FrameLayout(activity)
        val opaque = View(activity).apply { background = opaqueBackground() }
        val transparent = View(activity)
        overlay.addView(opaque)      // occluding
        overlay.addView(transparent) // transparent, drawn on top
        container.addView(overlay)
        layoutFull(overlay to rect(), opaque to rect(), transparent to rect())

        // Part of the subtree occludes, so the container is not a root; only the transparent child is.
        val result = detect()
        assertEquals(listOf(transparent), result)
        assertFalse(result.contains(overlay))
        assertFalse(result.contains(opaque))
    }

    @Test
    fun transparentContainerWithOpaqueChildOutsideAdFrame_isRegistered() {
        // The container spans past the ad and its only opaque child sits entirely outside the ad's
        // frame, so it never occluded the ad — registering the whole container hides nothing OMID
        // measures.
        val overlay = FrameLayout(activity)
        val opaqueOutside = View(activity).apply { background = opaqueBackground() }
        overlay.addView(opaqueOutside)
        container.addView(overlay)
        layoutFull(
            overlay to rect(0, 0, 600, 600),          // covers the ad's 0..200 region and beyond
            opaqueOutside to rect(400, 400, 600, 600) // off the ad entirely
        )

        assertEquals(listOf<View>(overlay), detect())
    }

    // --- Not registered (opaque content) ---------------------------------------------------------

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

    // --- Not registered (hidden / transparent-alpha / non-overlapping) ---------------------------

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

    // --- Earlier siblings (drawn under the ad) are never candidates ------------------------------

    @Test
    fun transparentSiblingDrawnUnderAd_notRegistered() {
        // Insert a transparent view *before* the ad in z-order; it sits under the ad, not on top.
        val under = View(activity)
        container.addView(under, 0)
        layoutFull(under to rect())

        assertTrue(detect().isEmpty())
    }

    // --- Guards -----------------------------------------------------------------------------------

    @Test
    fun nullAdView_returnsEmpty() {
        assertTrue(NativoFriendlyObstructionDetector().friendlyObstructionViews(null).isEmpty())
    }

    @Test
    fun detachedAdView_returnsEmpty() {
        assertTrue(
            NativoFriendlyObstructionDetector().friendlyObstructionViews(View(activity)).isEmpty()
        )
    }
}
