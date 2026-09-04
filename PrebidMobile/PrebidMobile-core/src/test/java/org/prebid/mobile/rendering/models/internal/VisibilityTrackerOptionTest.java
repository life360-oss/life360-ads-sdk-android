package org.prebid.mobile.rendering.models.internal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.prebid.mobile.rendering.models.ntv.NativeEventTracker;

/**
 * Locks the OpenRTB native event type to viewability threshold mapping, which is what decides when each of
 * a response's event trackers fires. Note that for IMPRESSION and OMID the "percentage" is read by
 * {@link org.prebid.mobile.rendering.utils.helpers.VisibilityChecker} as a minimum visible area in dips
 * instead, which is what makes those two one-pixel-in-view rather than one-percent-in-view.
 */
public class VisibilityTrackerOptionTest {

    @Test
    public void impression_isOnePixelInViewWithNoDwell() {
        VisibilityTrackerOption option =
                new VisibilityTrackerOption(NativeEventTracker.EventType.IMPRESSION);

        assertEquals(1, option.getMinVisibilityPercentage());
        assertEquals(0, option.getMinimumVisibleMillis());
    }

    @Test
    public void omid_matchesImpression() {
        VisibilityTrackerOption option =
                new VisibilityTrackerOption(NativeEventTracker.EventType.OMID);

        assertEquals(1, option.getMinVisibilityPercentage());
        assertEquals(0, option.getMinimumVisibleMillis());
    }

    @Test
    public void viewableMrc50_isHalfInViewForOneSecond() {
        VisibilityTrackerOption option =
                new VisibilityTrackerOption(NativeEventTracker.EventType.VIEWABLE_MRC50);

        assertEquals(50, option.getMinVisibilityPercentage());
        assertEquals(1000, option.getMinimumVisibleMillis());
    }

    @Test
    public void viewableMrc100_isFullyInViewForOneSecond() {
        VisibilityTrackerOption option =
                new VisibilityTrackerOption(NativeEventTracker.EventType.VIEWABLE_MRC100);

        assertEquals(100, option.getMinVisibilityPercentage());
        assertEquals(1000, option.getMinimumVisibleMillis());
    }

    @Test
    public void viewableVideo50_isHalfInViewForTwoSeconds() {
        VisibilityTrackerOption option =
                new VisibilityTrackerOption(NativeEventTracker.EventType.VIEWABLE_VIDEO50);

        assertEquals(50, option.getMinVisibilityPercentage());
        assertEquals(2000, option.getMinimumVisibleMillis());
    }

}
