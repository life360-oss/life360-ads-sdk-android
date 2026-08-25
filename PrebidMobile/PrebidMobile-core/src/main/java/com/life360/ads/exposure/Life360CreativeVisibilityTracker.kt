package com.life360.ads.exposure

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import com.life360.ads.utils.Life360Utils
import com.life360.ads.utils.PausableCountDownTimer
import org.prebid.mobile.LogUtil
import org.prebid.mobile.rendering.models.CreativeVisibilityTracker
import org.prebid.mobile.rendering.models.VisibilityTracker
import org.prebid.mobile.rendering.models.internal.VisibilityTrackerOption
import org.prebid.mobile.rendering.models.internal.VisibilityTrackerResult
import org.prebid.mobile.rendering.utils.exposure.ViewExposure
import org.prebid.mobile.rendering.utils.exposure.ViewExposureChecker
import org.prebid.mobile.rendering.utils.helpers.VisibilityChecker
import org.prebid.mobile.rendering.views.webview.mraid.Views
import java.lang.ref.WeakReference
import java.util.Collections

/**
 * Wraps originalVisibilityTracker (pre-draw based) as the initial engine.
 * Scroll/layout listeners watch for proof it's safe to go cheaper — a scroll dispatch that
 * actually moves this view's bounds, not just any scroll in the window. Once seen, the pre-draw
 * tracker is stopped and this class's own timer-based check takes over for good; only one engine
 * ever runs at a time.
 *
 * Initially the goal was to use scroll-based tracking instead of constantly pinging the
 * view-hierarchy for visibility changes. However, Compose provides alternate methods of scrolling
 * without the callbacks we need, so we had to bring back the original VisibilityTracker.
 */
class Life360CreativeVisibilityTracker @JvmOverloads constructor(
    trackedView: View,
    visibilityTrackerOptionSet: Set<VisibilityTrackerOption>,
    private val proceedAfterImpTracking: Boolean = false
) : VisibilityTracker {

    private val trackedView: WeakReference<View> = WeakReference(trackedView)
    private val visibilityCheckerList: MutableList<VisibilityChecker> = ArrayList()
    private val viewabilityTimerMap: MutableMap<VisibilityChecker, PausableCountDownTimer> = mutableMapOf()
    private var visibilityTrackerListener: VisibilityTracker.VisibilityTrackerListener? = null

    // Safe fallback engine - works with scroll-less compositions
    private val originalVisibilityTracker: CreativeVisibilityTracker =
        CreativeVisibilityTracker(trackedView, visibilityTrackerOptionSet, proceedAfterImpTracking)

    // Forwards originalVisibilityTracker's notifications on to whichever listener is currently set - looked
    // up dynamically so a listener change after startVisibilityCheck is still honored.
    private val originalVisibilityForwardingListener =
        VisibilityTracker.VisibilityTrackerListener { result -> visibilityTrackerListener?.onVisibilityChanged(result) }

    private var weakViewTreeObserver: WeakReference<ViewTreeObserver?> = WeakReference(null)

    // One-directional per attached observer - reset only when startVisibilityCheck resolves a
    // genuinely different observer (e.g. an interstitial reopened in a new window), since that's
    // a legitimately new place to re-earn the optimization.
    private var originalVisibilityDetached = false

    private val lastKnownBounds = Rect()

    private val viewabilityCheckDebouncer: () -> Unit = Life360Utils.debounceAction(VISIBILITY_DEBOUNCE_MILLIS) {
        runViewabilityCheck()
    }

    private val onScrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        if (!originalVisibilityDetached) {
            val view = this.trackedView.get()
            if (view != null) {
                val currentBounds = Rect()
                view.getGlobalVisibleRect(currentBounds)
                if (currentBounds != lastKnownBounds) {
                    originalVisibilityDetached = true
                    originalVisibilityTracker.setVisibilityTrackerListener(null)
                    originalVisibilityTracker.stopVisibilityCheck()
                }
                lastKnownBounds.set(currentBounds)
            }
        }
        if (originalVisibilityDetached) {
            viewabilityCheckDebouncer.invoke()
        }
    }

    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        // Refresh the baseline here too, not just on scroll: otherwise a layout-driven bounds
        // change could later be misattributed to an unrelated scroll-changed dispatch elsewhere
        // in the window, falsely "proving" scroll-changed reliability for this view.
        this.trackedView.get()?.getGlobalVisibleRect(lastKnownBounds)
        if (originalVisibilityDetached) {
            viewabilityCheckDebouncer.invoke()
        }
    }

    constructor(
        trackedView: View,
        visibilityTrackerOption: VisibilityTrackerOption
    ) : this(trackedView, Collections.singleton(visibilityTrackerOption))

    constructor(
        trackedView: View,
        visibilityTrackerOption: VisibilityTrackerOption,
        proceedAfterImpTracking: Boolean
    ) : this(trackedView, Collections.singleton(visibilityTrackerOption), proceedAfterImpTracking)

    init {
        val viewExposureChecker = ViewExposureChecker()
        for (trackingOption in visibilityTrackerOptionSet) {
            visibilityCheckerList.add(VisibilityChecker(trackingOption, viewExposureChecker))
        }
    }

    override fun setVisibilityTrackerListener(visibilityTrackerListener: VisibilityTracker.VisibilityTrackerListener?) {
        this.visibilityTrackerListener = visibilityTrackerListener
    }

    override fun startVisibilityCheck(context: Context) {
        val tracked = trackedView.get()
        if (tracked == null) {
            LogUtil.error(TAG, "Couldn't start visibility check. Target view is null")
            return
        }

        originalVisibilityTracker.setVisibilityTrackerListener(originalVisibilityForwardingListener)
        originalVisibilityTracker.startVisibilityCheck(context)

        tracked.getGlobalVisibleRect(lastKnownBounds)

        val viewTreeObserver = resolveViewTreeObserver(context, tracked) ?: return
        if (weakViewTreeObserver.get() === viewTreeObserver) {
            return
        }
        originalVisibilityDetached = false
        viewTreeObserver.addOnScrollChangedListener(onScrollChangedListener)
        viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        weakViewTreeObserver = WeakReference(viewTreeObserver)
    }

    /**
     * Used for interstitial cases, when the ad is opened in the new view hierarchy or received the new window focus.
     */
    override fun restartVisibilityCheck() {
        if (originalVisibilityDetached) {
            viewabilityCheckDebouncer.invoke()
        } else {
            originalVisibilityTracker.restartVisibilityCheck()
        }
    }

    override fun stopVisibilityCheck() {
        val viewTreeObserver = weakViewTreeObserver.get()
        if (viewTreeObserver != null && viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnScrollChangedListener(onScrollChangedListener)
            viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        }
        weakViewTreeObserver = WeakReference(null)
        originalVisibilityTracker.stopVisibilityCheck()
    }

    private fun resolveViewTreeObserver(context: Context?, view: View?): ViewTreeObserver? {
        val rootView = Views.getTopmostView(context, view) ?: return null
        val viewTreeObserver = rootView.viewTreeObserver
        return if (viewTreeObserver != null && viewTreeObserver.isAlive) viewTreeObserver else null
    }

    private fun runViewabilityCheck() {
        val trackedView = this.trackedView.get()
        if (trackedView == null) {
            stopVisibilityCheck()
            return
        }

        for (visibilityChecker in visibilityCheckerList) {
            val viewExposure: ViewExposure = visibilityChecker.checkViewExposure(trackedView)
            var shouldFireImpression = false
            val isVisible = visibilityChecker.isVisible(trackedView, viewExposure)

            // Manage viewability timer to ensure ad is visible for set duration before imp is fired
            val visibilityTrackerOption = visibilityChecker.visibilityTrackerOption
            var viewabilityTimer = viewabilityTimerMap[visibilityChecker]
            if (isVisible) {
                // Start viewability duration timer, which will call runViewabilityCheck() again when finished
                if (viewabilityTimer == null) {
                    val viewableImpDuration = visibilityTrackerOption.minimumVisibleMillis.toLong()
                    viewabilityTimer = PausableCountDownTimer(viewableImpDuration) {
                        runViewabilityCheck()
                    }
                    viewabilityTimerMap[visibilityChecker] = viewabilityTimer
                    viewabilityTimer.start()
                }

                if (viewabilityTimer.isFinished && !visibilityTrackerOption.isImpressionTracked) {
                    shouldFireImpression = true
                    visibilityTrackerOption.isImpressionTracked = true
                }
            } else {
                viewabilityTimer?.pause()
            }

            val visibilityTrackerResult = VisibilityTrackerResult(
                visibilityTrackerOption.eventType,
                viewExposure,
                isVisible,
                shouldFireImpression
            )
            notifyListener(visibilityTrackerResult)
        }

        // If all impressions are done and no further tracking is required, fully stop.
        if (allImpressionsFired() && !proceedAfterImpTracking) {
            stopVisibilityCheck()
        }
    }

    private fun notifyListener(visibilityTrackerResult: VisibilityTrackerResult) {
        visibilityTrackerListener?.onVisibilityChanged(visibilityTrackerResult)
    }

    private fun allImpressionsFired(): Boolean {
        for (visibilityChecker in visibilityCheckerList) {
            val visibilityTrackerOption = visibilityChecker.visibilityTrackerOption
            if (!visibilityTrackerOption.isImpressionTracked) {
                return false
            }
        }
        return true
    }

    companion object Companion {
        private val TAG = Life360CreativeVisibilityTracker::class.java.simpleName

        // Time interval to use for throttling visibility checks and debounce window for events.
        private const val VISIBILITY_DEBOUNCE_MILLIS = 150L
    }
}
