package com.life360.ads.exposure

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers [ViewOpacity.paintsOpaqueContent], the per-view opacity heuristic shared by OMID
 * friendly-obstruction detection and exposure obstruction counting. The ambiguous-leaf rule (plain
 * View transparent, unknown subclass opaque) is what keeps both callers on their safe side.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class ViewOpacityTest {

    private lateinit var activity: Activity

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    private fun opaque() = ColorDrawable(Color.RED)

    @Test
    fun plainView_paintsNothing() {
        assertFalse(ViewOpacity.paintsOpaqueContent(View(activity)))
    }

    @Test
    fun customViewSubclass_assumedOpaque() {
        val custom = object : View(activity) {}
        assertTrue(ViewOpacity.paintsOpaqueContent(custom))
    }

    @Test
    fun opaqueBackground_isOpaque() {
        assertTrue(ViewOpacity.paintsOpaqueContent(View(activity).apply { background = opaque() }))
    }

    @Test
    fun transparentBackground_isNotOpaque() {
        val view = View(activity).apply { background = ColorDrawable(Color.TRANSPARENT) }
        assertFalse(ViewOpacity.paintsOpaqueContent(view))
    }

    @Test
    fun opaqueForeground_isOpaque() {
        assertTrue(ViewOpacity.paintsOpaqueContent(View(activity).apply { foreground = opaque() }))
    }

    @Test
    fun imageViewWithDrawable_isOpaque() {
        val iv = ImageView(activity).apply { setImageDrawable(ColorDrawable(Color.BLUE)) }
        assertTrue(ViewOpacity.paintsOpaqueContent(iv))
    }

    @Test
    fun imageViewWithoutDrawable_isNotOpaque() {
        assertFalse(ViewOpacity.paintsOpaqueContent(ImageView(activity)))
    }

    @Test
    fun textViewWithText_isOpaque() {
        val tv = TextView(activity).apply {
            text = "hi"
            setTextColor(Color.BLACK)
        }
        assertTrue(ViewOpacity.paintsOpaqueContent(tv))
    }

    @Test
    fun textViewWithoutText_isNotOpaque() {
        assertFalse(ViewOpacity.paintsOpaqueContent(TextView(activity)))
    }

    @Test
    fun textViewWithTransparentTextColor_isNotOpaque() {
        val tv = TextView(activity).apply {
            text = "hi"
            setTextColor(Color.TRANSPARENT)
        }
        assertFalse(ViewOpacity.paintsOpaqueContent(tv))
    }

    @Test
    fun webView_isOpaque() {
        assertTrue(ViewOpacity.paintsOpaqueContent(WebView(activity)))
    }

    @Test
    fun viewGroupWithoutBackground_isNotOpaque() {
        // A group's own paint is only its background/foreground; its children are the caller's concern.
        assertFalse(ViewOpacity.paintsOpaqueContent(FrameLayout(activity)))
    }

    @Test
    fun viewGroupWithOpaqueBackground_isOpaque() {
        assertTrue(ViewOpacity.paintsOpaqueContent(FrameLayout(activity).apply { background = opaque() }))
    }
}
