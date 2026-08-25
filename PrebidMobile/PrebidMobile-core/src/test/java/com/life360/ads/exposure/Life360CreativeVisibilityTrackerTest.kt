package com.life360.ads.exposure

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import com.life360.ads.utils.PausableCountDownTimer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.prebid.mobile.rendering.models.CreativeVisibilityTracker
import org.prebid.mobile.rendering.models.VisibilityTracker
import org.prebid.mobile.rendering.models.internal.VisibilityTrackerOption
import org.prebid.mobile.rendering.models.ntv.NativeEventTracker
import org.prebid.mobile.test.utils.WhiteBox
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.LEGACY
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "w800dp-h800dp-xhdpi")
@LooperMode(LEGACY)
class Life360CreativeVisibilityTrackerTest {

    private lateinit var activity: Activity
    private lateinit var container: FrameLayout
    private lateinit var trackedView: View
    private lateinit var tracker: Life360CreativeVisibilityTracker
    private lateinit var mockListener: VisibilityTracker.VisibilityTrackerListener

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
        trackedView = View(activity)
        container.addView(trackedView)
        trackedView.layout(0, 0, 100, 100)

        mockListener = mock(VisibilityTracker.VisibilityTrackerListener::class.java)
        tracker = Life360CreativeVisibilityTracker(
            trackedView,
            VisibilityTrackerOption(NativeEventTracker.EventType.VIEWABLE_MRC50)
        )
        tracker.setVisibilityTrackerListener(mockListener)
        tracker.startVisibilityCheck(activity)
    }

    private fun preDrawTracker(): CreativeVisibilityTracker = WhiteBox.getInternalState(tracker, "preDrawTracker")

    private fun isPreDrawTrackerScheduled(): Boolean =
        WhiteBox.getInternalState(preDrawTracker(), "isVisibilityScheduled")

    private fun resetPreDrawTrackerScheduled() {
        WhiteBox.setInternalState(preDrawTracker(), "isVisibilityScheduled", false)
    }

    private fun isPreDrawDetached(): Boolean = WhiteBox.getInternalState(tracker, "preDrawDetached")

    private fun observer() = trackedView.viewTreeObserver

    /** Moves the tracked view so a later bounds comparison sees a real change. */
    private fun moveTrackedView() {
        trackedView.layout(0, -50, 100, 50)
    }

    /** Invokes the tracker's own scroll-changed listener directly - see class doc for why. */
    private fun fireScrollChanged() {
        val listener: ViewTreeObserver.OnScrollChangedListener = WhiteBox.getInternalState(tracker, "onScrollChangedListener")
        listener.onScrollChanged()
    }

    private fun fireGlobalLayout() {
        val listener: ViewTreeObserver.OnGlobalLayoutListener = WhiteBox.getInternalState(tracker, "onGlobalLayoutListener")
        listener.onGlobalLayout()
    }

    /** Clears the debounce window so the next trigger to this class's own engine actually runs. */
    private fun clearDebounceWindow() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(200))
    }

    @Test
    fun preDrawTracker_alone_schedulesCheck_beforeAnyScroll() {
        resetPreDrawTrackerScheduled()

        observer().dispatchOnPreDraw()

        assertTrue(isPreDrawTrackerScheduled())
        assertFalse(isPreDrawDetached())
    }

    @Test
    fun preDrawTracker_forwardsNotifications_toThisClasssListener() {
        val runnable: Runnable = WhiteBox.getInternalState(preDrawTracker(), "visibilityRunnable")

        runnable.run()

        verify(mockListener, atLeastOnce()).onVisibilityChanged(any())
    }

    @Test
    fun scrollChanged_withoutBoundsChange_doesNotDemote() {
        fireScrollChanged()

        assertFalse(isPreDrawDetached())

        // preDrawTracker must still be doing real work afterward.
        resetPreDrawTrackerScheduled()
        observer().dispatchOnPreDraw()
        assertTrue(isPreDrawTrackerScheduled())
    }

    @Test
    fun scrollChanged_withBoundsChange_demotesAndStopsPreDrawTracker() {
        moveTrackedView()

        fireScrollChanged()

        assertTrue(isPreDrawDetached())

        // preDrawTracker was really stopped, not just bypassed: its own listener is detached, so a
        // real dispatch no longer schedules anything on it.
        resetPreDrawTrackerScheduled()
        observer().dispatchOnPreDraw()
        assertFalse(isPreDrawTrackerScheduled())
    }

    @Test
    fun afterDemotion_scrollChangedDrivesThisClasssOwnEngine() {
        moveTrackedView()
        fireScrollChanged() // demotes, and this same dispatch already kicks off the first check
        clearInvocations(mockListener)
        clearDebounceWindow()

        fireScrollChanged()

        verify(mockListener, atLeastOnce()).onVisibilityChanged(any())
    }

    @Test
    fun afterDemotion_globalLayoutDrivesThisClasssOwnEngine() {
        moveTrackedView()
        fireScrollChanged() // demote
        clearInvocations(mockListener)
        clearDebounceWindow()

        fireGlobalLayout()

        verify(mockListener, atLeastOnce()).onVisibilityChanged(any())
    }

    @Test
    fun beforeDemotion_scrollAndLayoutDoNotDriveThisClasssOwnEngine() {
        // Neither listener should invoke the debounced engine while preDrawTracker is still live -
        // it already covers every visual change via pre-draw, scroll-driven or not.
        fireScrollChanged()
        fireGlobalLayout()

        verify(mockListener, never()).onVisibilityChanged(any())
    }

    @Test
    fun startVisibilityCheck_calledAgainWithSameObserver_preservesDemotionState() {
        moveTrackedView()
        fireScrollChanged() // demote
        assertTrue(isPreDrawDetached())

        tracker.startVisibilityCheck(activity)

        assertTrue(isPreDrawDetached())
    }

    // Attach/detach wiring is verified against a mocked ViewTreeObserver, independent of the
    // real-hierarchy tracker above, since verifying which observer APIs get called is a plain
    // interaction check - it doesn't need (and a mock is more precise than) a real observer. Both
    // this class's own resolution and preDrawTracker's internal one resolve to the same mock
    // observer, since both call the same Views.getTopmostView(context, view) with the same args.
    @Test
    fun startVisibilityCheck_attachesAllThreeListeners() {
        val mockRootView = mock(View::class.java)
        val mockObserver = mock(ViewTreeObserver::class.java)
        val mockTrackedView = mock(View::class.java)
        `when`(mockTrackedView.context).thenReturn(activity.applicationContext)
        `when`(mockTrackedView.rootView).thenReturn(mockRootView)
        `when`(mockRootView.viewTreeObserver).thenReturn(mockObserver)
        `when`(mockObserver.isAlive).thenReturn(true)

        val mockedTracker = Life360CreativeVisibilityTracker(
            mockTrackedView,
            VisibilityTrackerOption(NativeEventTracker.EventType.IMPRESSION)
        )
        mockedTracker.startVisibilityCheck(activity.applicationContext)

        val preDrawTracker: CreativeVisibilityTracker = WhiteBox.getInternalState(mockedTracker, "preDrawTracker")
        val onPreDrawListener: ViewTreeObserver.OnPreDrawListener =
            WhiteBox.getInternalState(preDrawTracker, "onPreDrawListener")
        val onScrollChangedListener: ViewTreeObserver.OnScrollChangedListener =
            WhiteBox.getInternalState(mockedTracker, "onScrollChangedListener")
        val onGlobalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener =
            WhiteBox.getInternalState(mockedTracker, "onGlobalLayoutListener")

        verify(mockObserver).addOnPreDrawListener(onPreDrawListener)
        verify(mockObserver).addOnScrollChangedListener(onScrollChangedListener)
        verify(mockObserver).addOnGlobalLayoutListener(onGlobalLayoutListener)

        mockedTracker.stopVisibilityCheck()

        verify(mockObserver).removeOnPreDrawListener(onPreDrawListener)
        verify(mockObserver).removeOnScrollChangedListener(onScrollChangedListener)
        verify(mockObserver).removeOnGlobalLayoutListener(onGlobalLayoutListener)
    }

    // Guards against silently swapping this class's own duration-tracking for the superclass-style
    // latch: whatever drives a check, once this class's own engine is live, must land in *this*
    // class's viewabilityTimerMap/PausableCountDownTimer.
    @Test
    fun ownEngine_tracksDurationWithPausableCountDownTimer() {
        moveTrackedView()
        fireScrollChanged() // demotes; the debounce window may or may not let this one through
        clearDebounceWindow()
        fireScrollChanged() // guaranteed to run now that preDrawDetached is already true

        val timerMap: Map<*, PausableCountDownTimer> = WhiteBox.getInternalState(tracker, "viewabilityTimerMap")
        val timer = timerMap.values.singleOrNull()
        assertTrue(timer != null)
        assertFalse(timer!!.isFinished) // 1000ms minimum for VIEWABLE_MRC50 - not yet elapsed
    }
}
