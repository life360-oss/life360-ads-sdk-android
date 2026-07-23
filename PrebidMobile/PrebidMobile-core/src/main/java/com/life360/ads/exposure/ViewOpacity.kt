package com.life360.ads.exposure

import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView

/**
 * Shared per-view heuristic for whether a view paints non-transparent content of its own — the common
 * judgment behind both OMID friendly-obstruction detection ([NativoFriendlyObstructionManager]) and
 * exposure-based obstruction counting (`ViewExposureChecker`). Both callers walk the tree and recurse
 * into children themselves, so this answers only for the view in isolation, never its subtree.
 *
 * "When unsure, assume it paints": a plain [View] draws nothing, but an unrecognized custom subclass
 * may override `onDraw`, so it is treated as opaque. That keeps both callers on their safe side — the
 * detector won't hide a custom occluder from OMID, and the exposure checker won't over-report
 * viewability.
 */
object ViewOpacity {

    @JvmStatic
    fun paintsOpaqueContent(view: View): Boolean {
        if (isOpaque(view.background)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isOpaque(view.foreground)) return true
        return when {
            // WebView extends ViewGroup, so it must be matched before the generic ViewGroup case.
            view is WebView -> true
            view is ImageView -> view.drawable != null
            view is TextView -> !view.text.isNullOrEmpty() && (view.currentTextColor ushr 24) != 0
            // A group's own paint is its background/foreground (checked above); its children are the
            // callers' concern via their own recursion.
            view is ViewGroup -> false
            // A plain View draws nothing on its own...
            view.javaClass == View::class.java -> false
            // ...but an unknown custom subclass may override onDraw, so assume it paints.
            else -> true
        }
    }

    // Drawable.getAlpha() is available since API 19
    private fun isOpaque(drawable: Drawable?): Boolean = drawable != null && drawable.alpha != 0
}
