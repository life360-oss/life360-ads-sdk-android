package org.prebid.mobile;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import android.view.View;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.life360.ads.exposure.Life360CreativeVisibilityTracker;
import com.life360.ads.om.NativeOMResource;
import org.prebid.mobile.rendering.models.internal.VisibilityTrackerOption;
import org.prebid.mobile.rendering.models.ntv.NativeEventTracker;
import org.prebid.mobile.rendering.utils.helpers.VisibilityChecker;
import org.prebid.mobile.rendering.models.internal.VisibilityTrackerResult;
import org.prebid.mobile.rendering.session.manager.OmAdSessionManager;
import org.prebid.mobile.reflection.Reflection;
import org.prebid.mobile.test.utils.ResourceUtils;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class PrebidNativeAdTest {

    /**
     * registerView() starts an Open Measurement session, which builds the JSLibraryManager singleton from
     * the mocked view's Context. Left in place it would outlive this class and starve later tests of assets.
     */
    @After
    public void cleanUp() throws IllegalAccessException {
        org.prebid.mobile.test.utils.WhiteBox.setStaticVariableTo(
                org.prebid.mobile.rendering.sdk.JSLibraryManager.class, "sInstance", null);
    }

    @Test
    public void registerView_withAllTrackers() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/Full.json");

        assertEquals("https://prebid.qa.openx.net//event?t=win&b=5f6bec03-a3ae-4084-b2ae-dedfb0ac01ff&a=b4eb1475-4e3d-4186-97b7-25b6a6cf8618&bidder=openx&ts=1643899069308", nativeAd.getWinEvent());
        assertEquals("https://prebid.qa.openx.net//event?t=imp&b=5f6bec03-a3ae-4084-b2ae-dedfb0ac01ff&a=b4eb1475-4e3d-4186-97b7-25b6a6cf8618&bidder=openx&ts=1643899069308", nativeAd.getImpEvent());

        // The fixture's only eventtracker is the OMID verification script (event 555, method 2). It is a
        // script for the OM SDK to load, so it must not become an impression pixel — fetching it once over
        // HTTP neither measures anything nor is an impression any vendor recorded. Prebid's own imp event
        // is all that is left on the impression threshold.
        List<String> impressionUrls =
                reflectEventTrackerUrls(nativeAd, NativeEventTracker.EventType.IMPRESSION);
        assertNotNull(impressionUrls);
        assertEquals(1, impressionUrls.size());
        assertThat(impressionUrls, hasItem("https://prebid.qa.openx.net//event?t=imp&b=5f6bec03-a3ae-4084-b2ae-dedfb0ac01ff&a=b4eb1475-4e3d-4186-97b7-25b6a6cf8618&bidder=openx&ts=1643899069308"));


        nativeAd.registerView(createViewMock(), mock(List.class), mock(PrebidNativeAdEventListener.class));


        assertEquals(
                java.util.Collections.singleton(NativeEventTracker.EventType.IMPRESSION),
                reflectTrackedEventTypes(nativeAd)
        );
    }

    /**
     * The OMID tracker is the only one the pixel path skips. A plain JS impression tracker still has to be
     * fired, or narrowing the OM predicate would silently lose impressions instead of measuring them.
     */
    @Test
    public void create_keepsNonOmidTrackersOnThePixelPath() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/MixedEventTrackers.json");

        List<String> impressionUrls =
                reflectEventTrackerUrls(nativeAd, NativeEventTracker.EventType.IMPRESSION);
        assertNotNull(impressionUrls);
        assertThat(impressionUrls, hasItem("https://example.com/pixel.gif"));
        assertThat(impressionUrls, hasItem("https://example.com/imp-tracker.js"));

        // ...while the event 555 resource went to Open Measurement instead.
        NativeOMResource resource = Reflection.getFieldOf(nativeAd, "omResource");
        assertNotNull(resource);
        assertEquals("iabtechlab.com-omid", resource.getVendorKey());
    }

    /**
     * The impression threshold is what signals Open Measurement and the publisher callback. Firing once per
     * threshold is the tracker's job — it flips its own per-option isImpressionTracked before reporting — so
     * what matters here is that the right event type triggers the right side effects.
     */
    @Test
    public void onVisibilityEvent_impressionThresholdSignalsOmAndListener() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/Full.json");
        OmAdSessionManager sessionManager = mock(OmAdSessionManager.class);
        PrebidNativeAdEventListener listener = mock(PrebidNativeAdEventListener.class);
        Reflection.setVariableTo(nativeAd, "omAdSessionManager", sessionManager);
        Reflection.setVariableTo(nativeAd, "listener", listener);

        nativeAd.onVisibilityEvent(
                visibilityResult(NativeEventTracker.EventType.IMPRESSION, true, true));

        verify(sessionManager).registerImpression();
        verify(listener).onAdImpression();
    }

    @Test
    public void onVisibilityEvent_belowThresholdSignalsNothing() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/Full.json");
        OmAdSessionManager sessionManager = mock(OmAdSessionManager.class);
        Reflection.setVariableTo(nativeAd, "omAdSessionManager", sessionManager);

        // Visible, but the dwell has not elapsed.
        nativeAd.onVisibilityEvent(
                visibilityResult(NativeEventTracker.EventType.IMPRESSION, true, false));
        // Threshold met, but the ad is off screen.
        nativeAd.onVisibilityEvent(
                visibilityResult(NativeEventTracker.EventType.IMPRESSION, false, true));

        verify(sessionManager, never()).registerImpression();
    }

    /**
     * A viewability threshold is not an impression. Signalling Open Measurement when MRC50 is reached would
     * report a viewable impression as though the ad had only just rendered.
     */
    @Test
    public void onVisibilityEvent_viewabilityThresholdDoesNotSignalOmImpression() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/Full.json");
        OmAdSessionManager sessionManager = mock(OmAdSessionManager.class);
        PrebidNativeAdEventListener listener = mock(PrebidNativeAdEventListener.class);
        Reflection.setVariableTo(nativeAd, "omAdSessionManager", sessionManager);
        Reflection.setVariableTo(nativeAd, "listener", listener);

        nativeAd.onVisibilityEvent(
                visibilityResult(NativeEventTracker.EventType.VIEWABLE_MRC50, true, true));

        verify(sessionManager, never()).registerImpression();
        verify(listener, never()).onAdImpression();
    }

    /**
     * One option per event type the response declared, so each group of trackers is gated by its own
     * threshold instead of all of them firing when the first one is met.
     */
    @Test
    public void registerView_tracksOneThresholdPerDeclaredEventType() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/MultipleEventTypes.json");

        nativeAd.registerView(createViewMock(), mock(List.class), mock(PrebidNativeAdEventListener.class));

        assertEquals(
                new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                        NativeEventTracker.EventType.IMPRESSION,
                        NativeEventTracker.EventType.VIEWABLE_MRC50,
                        NativeEventTracker.EventType.VIEWABLE_MRC100
                )),
                reflectTrackedEventTypes(nativeAd)
        );

        assertThat(reflectEventTrackerUrls(nativeAd, NativeEventTracker.EventType.VIEWABLE_MRC50),
                hasItem("https://example.com/mrc50.gif"));
        assertThat(reflectEventTrackerUrls(nativeAd, NativeEventTracker.EventType.VIEWABLE_MRC100),
                hasItem("https://example.com/mrc100.gif"));
    }

    /** An event type the SDK has no threshold for still has to fire, so it falls back to the impression. */
    @Test
    public void create_unknownEventTypeFallsBackToImpression() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/MultipleEventTypes.json");

        assertThat(reflectEventTrackerUrls(nativeAd, NativeEventTracker.EventType.IMPRESSION),
                hasItem("https://example.com/custom-777.gif"));
    }

    private VisibilityTrackerResult visibilityResult(
            NativeEventTracker.EventType eventType,
            boolean isVisible,
            boolean shouldFireImpression
    ) {
        VisibilityTrackerResult result = mock(VisibilityTrackerResult.class);
        when(result.getEventType()).thenReturn(eventType);
        when(result.isVisible()).thenReturn(isVisible);
        when(result.shouldFireImpression()).thenReturn(shouldFireImpression);
        return result;
    }

    /** The same fixture's verification script must instead reach Open Measurement as a resource. */
    @Test
    public void create_resolvesOpenMeasurementResourceFromEventTracker() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/Full.json");

        NativeOMResource resource = (NativeOMResource) Reflection
                .getFieldOf(nativeAd, "omResource");

        assertNotNull(resource);
        assertEquals("https://s3-us-west-2.amazonaws.com/omsdk-files/compliance-js/omid-validation-verification-script-v1.js", resource.getUrl());
        assertEquals("iabtechlab.com-omid", resource.getVendorKey());
        assertEquals("iabtechlab-Openx", resource.getVerificationParameters());
    }

    @Test
    public void create_withoutVerificationScript_resolvesNoOpenMeasurementResource() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/WithoutTrackers.json");

        assertNull(Reflection.getFieldOf(nativeAd, "omResource"));
    }

    @Test
    public void registerView_withoutTrackers() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/WithoutTrackers.json");

        assertNull(nativeAd.getWinEvent());
        assertNull(nativeAd.getImpEvent());
        assertTrue(reflectEventTrackerUrls(nativeAd).isEmpty());


        nativeAd.registerView(createViewMock(), mock(List.class), mock(PrebidNativeAdEventListener.class));


        // Nothing to measure and no OM resource, so no tracker is started at all.
        assertTrue(reflectTrackedEventTypes(nativeAd).isEmpty());
    }

    @Test
    public void nativeAdParser() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/Full.json");

        assertNotNull(nativeAd);

        assertEquals("OpenX (Title)", nativeAd.getTitle());
        assertEquals("https://www.saashub.com/images/app/service_logos/5/1df363c9a850/large.png?1525414023", nativeAd.getIconUrl());
        assertEquals("https://ssl-i.cdn.openx.com/mobile/demo-creatives/mobile-demo-banner-640x100.png", nativeAd.getImageUrl());
        assertEquals("Click here to visit our site!", nativeAd.getCallToAction());
        assertEquals("Learn all about this awesome story of someone using out OpenX SDK.", nativeAd.getDescription());
        assertEquals("OpenX (Brand)", nativeAd.getSponsoredBy());
        assertEquals("https://www.openx.com/", nativeAd.getClickUrl());

        ArrayList<NativeData> dataList = nativeAd.getDataList();
        assertEquals(5, dataList.size());
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.SPONSORED_BY, "OpenX (Brand)")));
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.DESCRIPTION, "Learn all about this awesome story of someone using out OpenX SDK.")));
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.CALL_TO_ACTION, "Click here to visit our site!")));
        assertThat(dataList, hasItem(new NativeData(500, "Sample value")));
        assertThat(dataList, hasItem(new NativeData(0, "Sample value 2")));

        ArrayList<NativeTitle> titlesList = nativeAd.getTitles();
        assertEquals(1, titlesList.size());
        assertThat(titlesList, hasItem(new NativeTitle("OpenX (Title)")));

        ArrayList<NativeImage> imagesList = nativeAd.getImages();
        assertEquals(4, imagesList.size());
        assertThat(imagesList, hasItem(new NativeImage(NativeImage.Type.ICON, "https://www.saashub.com/images/app/service_logos/5/1df363c9a850/large.png?1525414023")));
        assertThat(imagesList, hasItem(new NativeImage(NativeImage.Type.MAIN_IMAGE, "https://ssl-i.cdn.openx.com/mobile/demo-creatives/mobile-demo-banner-640x100.png")));
        assertThat(imagesList, hasItem(new NativeImage(500, "https://test.com/test.png")));
        assertThat(imagesList, hasItem(new NativeImage(0, "https://test2.com/test.png")));

        for (NativeImage image : imagesList) {
            if (image.getType() == NativeImage.Type.CUSTOM) {
                if (image.getUrl().equals("https://test.com/test.png")) {
                    assertEquals(500, image.getTypeNumber());
                } else if (image.getUrl().equals("https://test2.com/test.png")) {
                    assertEquals(0, image.getTypeNumber());
                }
            }
        }
    }

    @Test
    public void nativeAdWithWrapperParser() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/FullWithNativeWrapper.json");

        assertNotNull(nativeAd);

        assertEquals("OpenX (Title)", nativeAd.getTitle());
        assertEquals("https://www.saashub.com/images/app/service_logos/5/1df363c9a850/large.png?1525414023", nativeAd.getIconUrl());
        assertEquals("https://ssl-i.cdn.openx.com/mobile/demo-creatives/mobile-demo-banner-640x100.png", nativeAd.getImageUrl());
        assertEquals("Click here to visit our site!", nativeAd.getCallToAction());
        assertEquals("Learn all about this awesome story of someone using out OpenX SDK.", nativeAd.getDescription());
        assertEquals("OpenX (Brand)", nativeAd.getSponsoredBy());
        assertEquals("https://www.openx.com/", nativeAd.getClickUrl());

        ArrayList<NativeData> dataList = nativeAd.getDataList();
        assertEquals(5, dataList.size());
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.SPONSORED_BY, "OpenX (Brand)")));
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.DESCRIPTION, "Learn all about this awesome story of someone using out OpenX SDK.")));
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.CALL_TO_ACTION, "Click here to visit our site!")));
        assertThat(dataList, hasItem(new NativeData(500, "Sample value")));
        assertThat(dataList, hasItem(new NativeData(0, "Sample value 2")));

        ArrayList<NativeTitle> titlesList = nativeAd.getTitles();
        assertEquals(1, titlesList.size());
        assertThat(titlesList, hasItem(new NativeTitle("OpenX (Title)")));

        ArrayList<NativeImage> imagesList = nativeAd.getImages();
        assertEquals(4, imagesList.size());
        assertThat(imagesList, hasItem(new NativeImage(NativeImage.Type.ICON, "https://www.saashub.com/images/app/service_logos/5/1df363c9a850/large.png?1525414023")));
        assertThat(imagesList, hasItem(new NativeImage(NativeImage.Type.MAIN_IMAGE, "https://ssl-i.cdn.openx.com/mobile/demo-creatives/mobile-demo-banner-640x100.png")));
        assertThat(imagesList, hasItem(new NativeImage(500, "https://test.com/test.png")));
        assertThat(imagesList, hasItem(new NativeImage(0, "https://test2.com/test.png")));

        for (NativeImage image : imagesList) {
            if (image.getType() == NativeImage.Type.CUSTOM) {
                if (image.getUrl().equals("https://test.com/test.png")) {
                    assertEquals(500, image.getTypeNumber());
                } else if (image.getUrl().equals("https://test2.com/test.png")) {
                    assertEquals(0, image.getTypeNumber());
                }
            }
        }
    }

    private PrebidNativeAd nativeAdFromFile(String path) {
        String resource = ResourceUtils.convertResourceToString(path);
        String cacheId = CacheManager.save(resource);
        return PrebidNativeAd.create(cacheId);
    }

    private View createViewMock() {
        Context contextMock = mock(Context.class);
        when(contextMock.getApplicationContext()).thenReturn(mock(Application.class));

        View mainMock = mock(View.class);
        when(mainMock.getContext()).thenReturn(contextMock);
        return mainMock;
    }

    private Map<NativeEventTracker.EventType, List<String>> reflectEventTrackerUrls(PrebidNativeAd ad) {
        return Reflection.getFieldOf(ad, "eventTrackerUrls");
    }

    private List<String> reflectEventTrackerUrls(PrebidNativeAd ad, NativeEventTracker.EventType type) {
        return reflectEventTrackerUrls(ad).get(type);
    }

    private Set<VisibilityTrackerOption> reflectTrackedOptions(PrebidNativeAd ad) {
        Life360CreativeVisibilityTracker tracker = Reflection.getFieldOf(ad, "visibilityTracker");
        if (tracker == null) {
            return java.util.Collections.emptySet();
        }
        List<VisibilityChecker> checkers = Reflection.getFieldOf(tracker, "visibilityCheckerList");
        Set<VisibilityTrackerOption> options = new java.util.LinkedHashSet<>();
        for (VisibilityChecker checker : checkers) {
            options.add(checker.getVisibilityTrackerOption());
        }
        return options;
    }

    private Set<NativeEventTracker.EventType> reflectTrackedEventTypes(PrebidNativeAd ad) {
        Set<NativeEventTracker.EventType> types = new java.util.LinkedHashSet<>();
        for (VisibilityTrackerOption option : reflectTrackedOptions(ad)) {
            types.add(option.getEventType());
        }
        return types;
    }

}
