package com.life360.ads.exposure

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

/**
 * Detects the transparent overlays on top of an ad and tracks which are registered as OMID friendly
 * obstructions, so callers apply only the changes as the ad scrolls under / out from overlays.
 *
 * OMID auto-detects occluders by walking the view tree and counts every overlapping, non-hidden,
 * alpha>0 view as an occluder — even one whose entire subtree paints nothing over the ad (transparent
 * background, or opaque content outside the ad's frame). That erodes measured viewability for
 * Nativo/HTML ads sitting under transparent overlays or gesture-only views; registering those overlays
 * as friendly obstructions (`FriendlyObstructionPurpose.NOT_VISIBLE`) stops OMID counting them.
 *
 * [friendlyObstructionViews] is a point-in-time snapshot. Because the OM session starts while the ad
 * may still be off-screen in a scrolling container, [reconcile] must be re-run from the viewability
 * cycle; it diffs the fresh snapshot against what is already registered and returns only the delta.
 */
class NativoFriendlyObstructionManager {

    /** The change in registered obstructions since the previous [reconcile]. */
    data class Reconciliation(val added: List<View>, val removed: List<View>)

    private val registered = mutableSetOf<View>()

    // region Reconciliation (stateful)

    /**
     * Re-detects the transparent overlays over [adView] and returns only the changes since the last
     * call — newly-qualifying views to register, and no-longer-qualifying ones (scrolled away,
     * detached, or turned opaque) to unregister. Callers apply the delta to the OM session.
     */
    fun reconcile(adView: View?): Reconciliation {
        val current = friendlyObstructionViews(adView).toSet()

        val added = current.filter { registered.add(it) }

        val removed = mutableListOf<View>()
        val iterator = registered.iterator()
        while (iterator.hasNext()) {
            val view = iterator.next()
            if (view !in current) {
                iterator.remove()
                removed.add(view)
            }
        }
        return Reconciliation(added, removed)
    }

    /**
     * Forgets all tracked obstructions without emitting a delta — for when the OM session ends and its
     * obstructions are torn down with it.
     */
    fun clear() {
        registered.clear()
    }

    // endregion

    // region Detection (stateless snapshot)

    /**
     * Walks the views drawn on top of [adView] — the later siblings up the ancestor chain, the same
     * set OMID can flag as occluders — and returns the maximal roots that paint nothing over the ad's
     * frame.
     */
    fun friendlyObstructionViews(adView: View?): List<View> {
        val result = mutableListOf<View>()
        if (adView == null) return result

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
        if (ViewOpacity.paintsOpaqueContent(view) && overlapsAd(view, adRect)) return true
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

    private fun screenRect(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    // endregion

    private companion object {
        // Treat near-zero alpha as fully transparent.
        const val ALPHA_EPSILON = 0.01f
    }
}
