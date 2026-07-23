package com.life360.ads.exposure

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView

/**
 * OMID auto-detects occluders by walking the view tree and counts every overlapping, non-hidden,
 * alpha>0 view as an occluder — even when that view paints nothing over the ad (transparent
 * background, or opaque content that falls outside the ad's frame). That erodes the measured viewable
 * area for Nativo/HTML ads sitting under transparent overlays or gesture-only views.
 *
 * This finds the on-top views that paint nothing over the ad's frame so callers can register them as
 * OMID friendly obstructions (`FriendlyObstructionPurpose.NOT_VISIBLE`), which stops OMID descending
 * into (and counting) those overlays.
 *
 * OMID only measures within the ad's frame and stops descending at a registered obstruction, so we
 * register the *maximal* qualifying view: register a container whenever nothing in its subtree paints
 * over the ad's frame — an opaque child is fine as long as it falls outside that frame, since it never
 * occluded the ad. Only when opaque content actually overlaps the ad do we skip the container and
 * descend to register the transparent sub-regions instead (registering the container there would also
 * hide the real occluder).
 */
class NativoFriendlyObstructionDetector {

    /**
     * Walks the views drawn on top of [adView] — the later siblings up the ancestor chain, the same
     * set OMID can flag as occluders — and returns the maximal roots that paint nothing over the ad's
     * frame.
     */
    fun friendlyObstructionViews(adView: View?): List<View> {
        val result = mutableListOf<View>()
        if (adView == null) return result

        // OMID measures within the ad's own bounds, so only content overlapping this rect matters.
        val adRect = screenRect(adView)
        if (adRect.isEmpty) return result

        var child: View = adView
        var parent = adView.parent as? ViewGroup ?: return result
        while (parent.isShown) {
            for (i in parent.indexOfChild(child) + 1 until parent.childCount) {
                collectRoots(parent.getChildAt(i), adRect, result)
            }
            child = parent
            parent = parent.parent as? ViewGroup ?: break
        }
        return result
    }

    /**
     * Adds the maximal overlapping subtrees that paint nothing over the ad's frame, rooted at or under
     * [view], to [out]: a subtree clean over the ad registers as one root; a subtree with opaque
     * content over the ad is split by descending.
     */
    private fun collectRoots(view: View, adRect: Rect, out: MutableList<View>) {
        if (isEffectivelyHidden(view) || !overlapsAd(view, adRect)) {
            return // hidden, or nothing here overlaps the ad
        }
        if (!subtreePaintsOpaqueOverAd(view, adRect)) {
            out.add(view) // nothing here paints over the ad -> maximal root; OMID stops descending here
            return
        }
        // Part of the subtree paints over the ad; keep the opaque region as an occluder and recurse
        // to register only the transparent children.
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectRoots(view.getChildAt(i), adRect, out)
            }
        }
    }

    /** True if [view] or any descendant paints non-transparent content intersecting the ad. */
    private fun subtreePaintsOpaqueOverAd(view: View, adRect: Rect): Boolean {
        if (isEffectivelyHidden(view)) return false
        if (viewPaintsOpaqueContent(view) && overlapsAd(view, adRect)) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (subtreePaintsOpaqueOverAd(view.getChildAt(i), adRect)) return true
            }
        }
        return false
    }

    /** Mirrors OMID: hidden, detached, zero-size, or near-transparent views neither occlude nor qualify. */
    private fun isEffectivelyHidden(view: View): Boolean {
        return !view.isShown || view.alpha <= ALPHA_EPSILON || view.width <= 0 || view.height <= 0
    }

    private fun overlapsAd(view: View, adRect: Rect): Boolean {
        return Rect.intersects(screenRect(view), adRect)
    }

    /**
     * Heuristic for whether [view] itself paints non-transparent content. Deliberately conservative:
     * unrecognized leaf types default to transparent, so only known painters count as occluders.
     */
    private fun viewPaintsOpaqueContent(view: View): Boolean {
        // Drawable.getAlpha() is available since API 19 (the module's minSdk).
        view.background?.let { if (it.alpha != 0) return true }
        return when (view) {
            is ImageView -> view.drawable != null
            is TextView -> !view.text.isNullOrEmpty() && (view.currentTextColor ushr 24) != 0
            is WebView -> true
            else -> false
        }
    }

    private fun screenRect(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private companion object {
        // Treat near-zero alpha as fully transparent.
        const val ALPHA_EPSILON = 0.01f
    }
}
