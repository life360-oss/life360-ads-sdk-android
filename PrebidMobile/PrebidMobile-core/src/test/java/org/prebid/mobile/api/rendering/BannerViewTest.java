/*
 *    Copyright 2018-2021 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile.api.rendering;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import com.life360.ads.bid.Life360BidResponse;
import com.life360.ads.server.NativoServerProxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.mobile.AdSize;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.data.AdUnitFormat;
import org.prebid.mobile.api.data.VideoPlacementType;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.api.rendering.listeners.BannerVideoListener;
import org.prebid.mobile.api.rendering.listeners.BannerViewListener;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.bidding.data.bid.Bid;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.interfaces.BannerEventHandler;
import org.prebid.mobile.rendering.bidding.interfaces.StandaloneBannerEventHandler;
import org.prebid.mobile.rendering.bidding.listeners.BannerEventListener;
import org.prebid.mobile.rendering.bidding.listeners.BidRequesterListener;
import org.prebid.mobile.rendering.bidding.listeners.DisplayVideoListener;
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener;
import com.life360.ads.Life360Ads;
import org.prebid.mobile.rendering.bidding.loader.BidLoader;
import org.prebid.mobile.rendering.models.AdPosition;
import org.prebid.mobile.rendering.utils.broadcast.ScreenStateReceiver;
import org.prebid.mobile.test.utils.WhiteBox;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 19)
public class BannerViewTest {

    private static final String AD_UNIT_ID = "12345678";
    private static final AdSize AD_SIZE = new AdSize(320, 50);

    private BannerView bannerView;

    private Context mockContext;
    @Mock
    private BidLoader mockBidLoader;
    @Mock
    private BannerEventHandler mockEventHandler;
    @Mock
    private BannerViewListener mockBannerListener;
    @Mock
    private BannerVideoListener mockBannerVideoListener;
    @Mock
    private DisplayView mockDisplayView;
    @Mock
    private ScreenStateReceiver mockScreenStateReceiver;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mockContext = Robolectric.buildActivity(Activity.class).create().get();

        when(mockEventHandler.getAdSizeArray()).thenReturn(new AdSize[]{AD_SIZE});
        bannerView = new BannerView(mockContext, AD_UNIT_ID, mockEventHandler);
        WhiteBox.field(BannerView.class, "bidLoader").set(bannerView, mockBidLoader);
        WhiteBox.field(BannerView.class, "displayView").set(bannerView, mockDisplayView);
        WhiteBox.field(BannerView.class, "displayView").set(bannerView, mockDisplayView);
        WhiteBox.field(BannerView.class, "screenStateReceiver").set(bannerView, mockScreenStateReceiver);
        bannerView.setBannerListener(mockBannerListener);
        bannerView.setBannerVideoListener(mockBannerVideoListener);

        assertEquals(AdPosition.UNDEFINED.getValue(), bannerView.getAdPosition().getValue());
    }

    @After
    public void tearDown() {
        Life360Ads.setPrebidServerEnabled(true);
    }

    @Test
    public void createPrebidBannerViewWithAttributes_InstanceCreated() {
        AttributeSet attributes = Robolectric.buildAttributeSet().build();
        BannerView bannerView = new BannerView(mockContext, attributes);

        assertNotNull(bannerView);
    }

    @Test
    public void createPrebidBannerViewNoEventHandler_InstanceCreatedAndBidLoaderIsNotNullAndStandaloneEventHandlerProvided()
        throws IllegalAccessException {
        BannerView bannerView = new BannerView(mockContext, AD_UNIT_ID, AD_SIZE);

        Object eventHandler = WhiteBox.field(BannerView.class, "eventHandler").get(bannerView);
        BidLoader bidLoader = ((BidLoader) WhiteBox.field(BannerView.class, "bidLoader").get(bannerView));

        assertNotNull(bannerView);
        assertTrue(eventHandler instanceof StandaloneBannerEventHandler);
        assertNotNull(bidLoader);
    }

    @Test
    public void loadAdWithNullBidLoader_NoExceptionIsThrown() throws IllegalAccessException {
        WhiteBox.field(BannerView.class, "bidLoader").set(bannerView, null);

        bannerView.loadAd();
    }

    @Test
    public void loadAdWithPrimaryAdServerRequestInProgress_DoNothing() {
        changePrimaryAdServerRequestStatus(true);
        bannerView.loadAd();

        verifyNoInteractions(mockBidLoader);
    }

    @Test
    public void loadAd_bidResponseIsInitialized() {
        bannerView.loadAd();

        BidResponse response = bannerView.getBidResponse();
        System.out.println(response);
    }

    @Test
    public void loadAdWithValidBidLoaderAndPrimaryAdRequestNotInProgress_ExecuteBidLoad() {
        bannerView.loadAd();

        verify(mockBidLoader, times(1)).load();
    }

    @Test
    public void destroy_DestroyEventHandlerAndBidLoader() {
        bannerView.destroy();

        verify(mockEventHandler).destroy();
        verify(mockBidLoader).destroy();
        verify(mockScreenStateReceiver).unregister();
    }

    @Test
    public void setAutoRefreshDelayInSec_EqualsGetAutoRefreshDelayInMs() {
        final int interval = 30;
        bannerView.setAutoRefreshDelay(interval);

        assertEquals(interval * 1000, bannerView.getAutoRefreshDelayInMs());
    }

    @Test
    public void setRefreshIntervalInSecSmallerThanZero_ExpectDefaultRefresh() {
        final int interval = -1;
        bannerView.setAutoRefreshDelay(interval);

        assertEquals(0, bannerView.getAutoRefreshDelayInMs());
    }

    @Test
    public void addAdditionalSizes_GetterAndAdUnitConfigContainSetValueAndInitialSize() {
        final AdSize adSize = new AdSize(10, 20);
        final Set<AdSize> expectedSet = new HashSet<>();
        expectedSet.add(AD_SIZE); // from eventHandler
        expectedSet.add(adSize);

        bannerView.addAdditionalSizes(adSize);

        assertEquals(expectedSet, bannerView.getAdditionalSizes());
    }

    @Test
    public void setVideoPlacementType_AdUnitIdentifierTypeIsVASTAndVideoPlacementIsUpdated()
        throws IllegalAccessException {
        final VideoPlacementType videoPlacement = VideoPlacementType.IN_BANNER;
        AdUnitConfiguration mockAdConfiguration = mock(AdUnitConfiguration.class);
        WhiteBox.field(BannerView.class, "adUnitConfig").set(bannerView, mockAdConfiguration);

        bannerView.setVideoPlacementType(videoPlacement);

        verify(mockAdConfiguration, times(1)).setPlacementType(eq(VideoPlacementType.mapToPlacementType(videoPlacement)));
        verify(mockAdConfiguration, times(1)).setAdFormat(eq(AdFormat.VAST));
    }

    @Test
    public void setAdVideoPlacement_EqualsGetVideoPlacement() {
        bannerView.setVideoPlacementType(null);
        assertNull(bannerView.getVideoPlacementType());

        bannerView.setVideoPlacementType(VideoPlacementType.IN_ARTICLE);
        assertEquals(VideoPlacementType.IN_ARTICLE, bannerView.getVideoPlacementType());

        bannerView.setVideoPlacementType(VideoPlacementType.IN_BANNER);
        assertEquals(VideoPlacementType.IN_BANNER, bannerView.getVideoPlacementType());

        bannerView.setVideoPlacementType(VideoPlacementType.IN_FEED);
        assertEquals(VideoPlacementType.IN_FEED, bannerView.getVideoPlacementType());
    }

    @Test
    public void setVideoPlacementType_afterExplicitAdUnitFormats_keepsConfiguredFormats() {
        bannerView.setAdUnitFormats(EnumSet.of(AdUnitFormat.BANNER, AdUnitFormat.VIDEO));

        bannerView.setVideoPlacementType(VideoPlacementType.IN_BANNER);

        assertEquals(EnumSet.of(AdUnitFormat.BANNER, AdUnitFormat.VIDEO), bannerView.getAdUnitFormats());
        assertEquals(VideoPlacementType.IN_BANNER, bannerView.getVideoPlacementType());
    }

    @Test
    public void setAdUnitFormats_afterVideoPlacementType_keepsConfiguredFormats() {
        bannerView.setVideoPlacementType(VideoPlacementType.IN_BANNER);

        bannerView.setAdUnitFormats(EnumSet.of(AdUnitFormat.BANNER, AdUnitFormat.VIDEO));

        assertEquals(EnumSet.of(AdUnitFormat.BANNER, AdUnitFormat.VIDEO), bannerView.getAdUnitFormats());
        assertEquals(VideoPlacementType.IN_BANNER, bannerView.getVideoPlacementType());
    }

    @Test
    public void adUnitFormatsDefaultToBanner() {
        assertEquals(EnumSet.of(AdUnitFormat.BANNER), bannerView.getAdUnitFormats());
    }

    @Test
    public void setAdUnitFormats_video_requestsVideoOnly() throws IllegalAccessException {
        bannerView.setAdUnitFormats(EnumSet.of(AdUnitFormat.VIDEO));

        assertEquals(EnumSet.of(AdUnitFormat.VIDEO), bannerView.getAdUnitFormats());
        assertEquals(EnumSet.of(AdFormat.VAST), getAdUnitConfig().getAdFormats());
    }

    @Test
    public void setAdUnitFormats_multiformat_requestsBannerAndVideo() throws IllegalAccessException {
        bannerView.setAdUnitFormats(EnumSet.of(AdUnitFormat.BANNER, AdUnitFormat.VIDEO));

        assertEquals(EnumSet.of(AdUnitFormat.BANNER, AdUnitFormat.VIDEO), bannerView.getAdUnitFormats());
        assertEquals(EnumSet.of(AdFormat.BANNER, AdFormat.VAST), getAdUnitConfig().getAdFormats());
    }

    @Test
    public void setAdUnitFormats_nullOrEmpty_keepsCurrentValue() throws IllegalAccessException {
        bannerView.setAdUnitFormats(EnumSet.of(AdUnitFormat.BANNER, AdUnitFormat.VIDEO));

        bannerView.setAdUnitFormats(null);
        assertEquals(EnumSet.of(AdFormat.BANNER, AdFormat.VAST), getAdUnitConfig().getAdFormats());

        bannerView.setAdUnitFormats(EnumSet.noneOf(AdUnitFormat.class));
        assertEquals(EnumSet.of(AdFormat.BANNER, AdFormat.VAST), getAdUnitConfig().getAdFormats());
    }

    @Test
    public void setAdUnitFormats_multiformat_autoRefreshStaysAvailable() {
        bannerView.setAdUnitFormats(EnumSet.of(AdUnitFormat.BANNER, AdUnitFormat.VIDEO));

        bannerView.setAutoRefreshDelay(30);

        assertEquals(30_000, bannerView.getAutoRefreshDelayInMs());
    }

    //region ================= Auto refresh vs. video creatives
    @Test
    public void canPerformRefreshWhileVideoIsPlaying_SkipTheTick() throws Exception {
        when(mockDisplayView.isVideoPlaying()).thenReturn(true);

        assertFalse(getBidRefreshListener().canPerformRefresh());
    }

    @Test
    public void canPerformRefreshAfterVideoFinished_AllowTheTick() throws Exception {
        when(mockDisplayView.isVideoPlaying()).thenReturn(false);
        when(mockScreenStateReceiver.isScreenOn()).thenReturn(true);

        // The visibility checker is the only remaining gate, so the video no longer blocks refresh.
        getBidRefreshListener().canPerformRefresh();

        verify(mockDisplayView, times(1)).isVideoPlaying();
    }

    @Test
    public void canPerformRefreshAfterAdFailed_AllowTheTickEvenIfVideoReportsPlaying() throws Exception {
        when(mockDisplayView.isVideoPlaying()).thenReturn(true);
        WhiteBox.field(BannerView.class, "adFailed").set(bannerView, true);

        assertTrue(getBidRefreshListener().canPerformRefresh());
    }

    @Test
    public void isVideoPlayingWithoutDisplayView_False() throws IllegalAccessException {
        WhiteBox.field(BannerView.class, "displayView").set(bannerView, null);

        assertFalse(bannerView.isVideoPlaying());
    }

    private BidLoader.BidRefreshListener getBidRefreshListener() throws Exception {
        // initBidLoader() builds a BidLoader and registers the refresh gate on it.
        WhiteBox.method(BannerView.class, "initBidLoader").invoke(bannerView);

        final BidLoader bidLoader = (BidLoader) WhiteBox.field(BannerView.class, "bidLoader").get(bannerView);
        return (BidLoader.BidRefreshListener) WhiteBox
                .field(BidLoader.class, "bidRefreshListener")
                .get(bidLoader);
    }

    private AdUnitConfiguration getAdUnitConfig() throws IllegalAccessException {
        return (AdUnitConfiguration) WhiteBox.field(BannerView.class, "adUnitConfig").get(bannerView);
    }
    //endregion ================= Auto refresh vs. video creatives

    //region ======================= BidRequestListener tests
    @Test
    public void onFetchComplete_AssignWinningBidMarkPrimaryServerRequestInProgressRequestAdWithBid() {
        final BidResponse mockBidResponse = mock(BidResponse.class);
        final Bid mockBid = mock(Bid.class);

        when(mockBidResponse.getWinningBid()).thenReturn(mockBid);

        BidRequesterListener listener = getBidRequesterListener();
        listener.onFetchCompleted(mockBidResponse);

        Bid winningBid = bannerView.getWinnerBid();
        BidResponse actualResponse = bannerView.getBidResponse();

        assertEquals(winningBid, mockBid);
        assertEquals(mockBidResponse, actualResponse);
        verify(mockEventHandler, times(1)).requestAdWithBid(eq(mockBidResponse));
        assertTrue(bannerView.isPrimaryAdServerRequestInProgress());
    }

    @Test
    public void onError_NullifyWinnerBidAndRequestAdWitNullBid() {
        final BidResponse mockBidResponse = mock(BidResponse.class);
        final Bid mockBid = mock(Bid.class);

        when(mockBidResponse.getWinningBid()).thenReturn(mockBid);
        bannerView.setBidResponse(mockBidResponse);

        BidRequesterListener listener = getBidRequesterListener();
        listener.onError(new AdException(AdException.INTERNAL_ERROR, "Test"));

        assertNull(bannerView.getWinnerBid());
        assertNull(bannerView.getBidResponse());
        verify(mockEventHandler, times(1)).requestAdWithBid(org.mockito.Mockito.isNull());
    }
    //endregion ======================= BidRequestListener tests

    //region ================= BannerEventListener tests
    @Test
    public void onSdkWinAndWinnerBidIsNull_AdRequestStatusIsFinishedNotifyErrorListener() {
        changePrimaryAdServerRequestStatus(true);
        final BannerEventListener bannerEventListener = getBannerEventListener();
        bannerView.setBidResponse(null);

        bannerEventListener.onSdkWin(null);

        verify(mockBannerListener, times(1)).onAdFailed(eq(bannerView), any(AdException.class));
        assertFalse(bannerView.isPrimaryAdServerRequestInProgress());
    }

    @Test
    public void onSdkWin_AdRequestStatusIsFinishedDisplayAdView() {
        changePrimaryAdServerRequestStatus(true);
        final BannerEventListener bannerEventListener = getBannerEventListener();
        final BidResponse mockBidResponse = mock(BidResponse.class);
        final Bid mockBid = mock(Bid.class);

        when(mockBidResponse.getWinningBid()).thenReturn(mockBid);
        when(mockBidResponse.getWinningBidWidthHeightPairDips(any())).thenReturn(Pair.create(0, 0));

        bannerView.setBidResponse(mockBidResponse);

        bannerEventListener.onSdkWin(null);

        assertFalse(bannerView.isPrimaryAdServerRequestInProgress());
    }

    @Test
    public void onAdServerWin_AdRequestStatusIsFinishedDisplayAdViewAndNotifyAdReceivedListener() {
        changePrimaryAdServerRequestStatus(true);

        final View mockView = mock(View.class);
        final BannerEventListener bannerEventListener = getBannerEventListener();

        bannerEventListener.onAdServerWin(mockView);

        assertFalse(bannerView.isPrimaryAdServerRequestInProgress());
        verify(mockBannerListener, times(1)).onAdLoaded(bannerView);
        // verify(spyBannerView).displayAdServerView(mockView);
    }

    @Test
    public void onFailedAndNoWinnerBid_AdRequestStatusIsFinishedNotifyErrorListener() {
        changePrimaryAdServerRequestStatus(true);
        bannerView.setBidResponse(null);

        final AdException exception = new AdException(AdException.INTERNAL_ERROR, "GAM error");
        final BannerEventListener bannerEventListener = getBannerEventListener();

        bannerEventListener.onAdFailed(exception);

        verify(mockBannerListener, times(1)).onAdFailed(bannerView, new AdException(AdException.FAILED_TO_LOAD_BIDS, "GAM status: \"Exception: SDK internal error, GAM error\". Prebid status: \"Exception: SDK internal error, Unknown exception\""));
    }

    @Test
    public void onFailedAndWithWinnerBid_AdRequestStatusIsFinishedNotifySdkWin() {
        changePrimaryAdServerRequestStatus(true);

        final BidResponse mockBidResponse = mock(BidResponse.class);
        final Bid mockBid = mock(Bid.class);
        final BannerEventListener spyEventListener = spy(getBannerEventListener());
        when(mockBidResponse.getWinningBid()).thenReturn(mockBid);
        when(mockBidResponse.getWinningBidWidthHeightPairDips(any())).thenReturn(Pair.create(0, 0));

        bannerView.setBidResponse(mockBidResponse);
        spyEventListener.onAdFailed(new AdException(AdException.INTERNAL_ERROR, "Test"));

        assertFalse(bannerView.isPrimaryAdServerRequestInProgress());
        verify(spyEventListener, times(1)).onSdkWin(any());
    }

    @Test
    public void onAdOpened_NotifyAdClickedListener() {
        final BannerEventListener bannerEventListener = getBannerEventListener();
        bannerEventListener.onAdClicked();

        verify(mockBannerListener, times(1)).onAdClicked(bannerView);
    }

    @Test
    public void onAdClosed_NotifyAdClosedListener() {
        final BannerEventListener bannerEventListener = getBannerEventListener();
        bannerEventListener.onAdClosed();

        verify(mockBannerListener, times(1)).onAdClosed(bannerView);
    }
    //endregion ================= BannerEventListener test

    private void changePrimaryAdServerRequestStatus(boolean isLoading) {
        try {
            WhiteBox.field(BannerView.class, "isPrimaryAdServerRequestInProgress").set(bannerView, isLoading);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void whenLoadAd_CallBidLoaderLoad() {
        bannerView.loadAd();
        verify(mockBidLoader).load();
    }

    @Test
    public void loadAdInServerlessMode_SkipBidLoaderRequestAdWithBidAndScheduleRefresh() throws Exception {
        // Serverless mode: no Prebid Server. The (real) Life360 request fails synchronously because no
        // context is set, so loadAd() falls through to the serverless branch. The mode is captured when the
        // view is built, so it has to be set before constructing rather than before loading.
        Life360Ads.setPrebidServerEnabled(false);
        BannerView serverlessBanner = newBannerViewWithMockBidLoader();

        serverlessBanner.loadAd();

        verify(mockBidLoader, never()).load();
        verify(mockEventHandler, times(1)).requestAdWithBid(org.mockito.Mockito.isNull());
        verify(mockBidLoader, times(1)).setupRefreshTimer();
    }

    @Test
    public void loadAdAfterPrebidServerAddedLater_ServerlessViewKeepsItsModeAndNewViewUsesPrebid()
            throws Exception {
        // The scenario a dual init produces: one view built serverless, a Prebid Server added afterwards,
        // then a second view. Each keeps the mode it was built with.
        Life360Ads.setPrebidServerEnabled(false);
        BannerView serverlessBanner = newBannerViewWithMockBidLoader();

        Life360Ads.setPrebidServerEnabled(true);
        BidLoader secondMockBidLoader = mock(BidLoader.class);
        BannerView prebidBanner = new BannerView(mockContext, AD_UNIT_ID, mockEventHandler);
        WhiteBox.field(BannerView.class, "bidLoader").set(prebidBanner, secondMockBidLoader);

        serverlessBanner.loadAd();
        prebidBanner.loadAd();

        verify(mockBidLoader, never()).load();
        verify(secondMockBidLoader, times(1)).load();
    }

    /**
     * A view whose BidLoader is mocked, so a load can be observed without a real bid request. Built fresh
     * rather than reusing the shared field because the Prebid Server mode is captured at construction.
     */
    private BannerView newBannerViewWithMockBidLoader() throws IllegalAccessException {
        BannerView view = new BannerView(mockContext, AD_UNIT_ID, mockEventHandler);
        WhiteBox.field(BannerView.class, "bidLoader").set(view, mockBidLoader);
        return view;
    }

    @Test
    public void whenDisplayViewOnAdLoaded_CallBannerListenerOnAdLoaded()
        throws IllegalAccessException {
        getDisplayViewListener().onAdLoaded();
        verify(mockBannerListener).onAdLoaded(bannerView);
    }

    //region ================= Bid expiration
    @Test
    public void whenLoadedBannerExpiresWithoutRefresh_KeepAdAndNotifyExpiredOnly() {
        bannerView.addView(new View(mockContext));
        receiveBid(1);
        getDisplayViewListener().onAdLoaded();

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        assertTrue(bannerView.isExpired());
        assertEquals(1, bannerView.getChildCount());
        verify(mockBannerListener).onAdExpired(bannerView);
        verify(mockBannerListener, never()).onAdFailed(eq(bannerView), any(AdException.class));
        verify(mockDisplayView, never()).destroy();
        verify(mockBidLoader, never()).load();
    }

    @Test
    public void whenLoadedBannerExpiresWithAutoRefresh_RemoveAdThenNotifyThenLoadAgain() {
        bannerView.setAutoRefreshDelay(30);
        receiveBid(1);
        getDisplayViewListener().onAdLoaded();

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        InOrder inOrder = inOrder(mockDisplayView, mockBannerListener, mockBidLoader);
        inOrder.verify(mockDisplayView).destroy();
        inOrder.verify(mockBannerListener).onAdExpired(bannerView);
        inOrder.verify(mockBidLoader).load();
        verify(mockBannerListener, never()).onAdFailed(eq(bannerView), any(AdException.class));
    }

    @Test
    public void whenRefreshStoppedBannerExpires_KeepAdAndDoNotLoadAgain() {
        bannerView.setAutoRefreshDelay(30);
        bannerView.stopRefresh();
        receiveBid(1);
        getDisplayViewListener().onAdLoaded();

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        verify(mockBannerListener).onAdExpired(bannerView);
        verify(mockDisplayView, never()).destroy();
        verify(mockBidLoader, never()).load();
    }

    @Test
    public void whenLoadAdAfterStopRefresh_ExpiredBannerLoadsAgain() {
        bannerView.setAutoRefreshDelay(30);
        bannerView.stopRefresh();
        bannerView.loadAd();
        receiveBid(1);
        getDisplayViewListener().onAdLoaded();

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        verify(mockBannerListener).onAdExpired(bannerView);
        verify(mockBidLoader, times(2)).load();
    }

    @Test
    public void whenBidExpiresWhileAdIsLoading_NotifyExpiredRightAfterLoaded() {
        receiveBid(1);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        verify(mockBannerListener, never()).onAdExpired(bannerView);

        getDisplayViewListener().onAdLoaded();

        InOrder inOrder = inOrder(mockBannerListener);
        inOrder.verify(mockBannerListener).onAdLoaded(bannerView);
        inOrder.verify(mockBannerListener).onAdExpired(bannerView);
        assertTrue(bannerView.isExpired());
    }

    @Test
    public void whenCreativeLoadsSlowly_ExpirationStillCountsFromBidReceipt() {
        receiveBid(2);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);
        getDisplayViewListener().onAdLoaded();

        verify(mockBannerListener, never()).onAdExpired(bannerView);

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        verify(mockBannerListener).onAdExpired(bannerView);
    }

    @Test
    public void whenBannerImpressionTrackedBeforeExpiration_DoNotNotifyExpired() {
        receiveBid(1);
        DisplayViewListener displayViewListener = getDisplayViewListener();

        displayViewListener.onAdLoaded();
        displayViewListener.onAdDisplayed();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        assertFalse(bannerView.isExpired());
        verify(mockBannerListener, never()).onAdExpired(bannerView);
        verify(mockDisplayView, never()).destroy();
    }

    @Test
    public void whenAdServerWins_DoNotNotifyExpired() {
        receiveBid(1);
        getBannerEventListener().onAdServerWin(new View(mockContext));

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        assertFalse(bannerView.isExpired());
        verify(mockBannerListener, never()).onAdExpired(bannerView);
    }

    @Test
    public void whenBannerDestroyedBeforeExpiration_DoNotNotifyExpired() {
        receiveBid(1);
        getDisplayViewListener().onAdLoaded();
        bannerView.destroy();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        assertFalse(bannerView.isExpired());
        verify(mockBannerListener, never()).onAdExpired(bannerView);
    }

    @Test
    public void whenNewBidWithoutExpirationArrives_PreviousAdStopsExpiring() {
        receiveBid(1);
        getDisplayViewListener().onAdLoaded();
        receiveBid(null);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        assertFalse(bannerView.isExpired());
        verify(mockBannerListener, never()).onAdExpired(bannerView);
    }

    @Test
    public void whenBannerLoadedWithoutExpiration_DoNotNotifyExpired() {
        receiveBid(null);
        getDisplayViewListener().onAdLoaded();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        assertFalse(bannerView.isExpired());
        verify(mockBannerListener, never()).onAdExpired(bannerView);
    }

    @Test
    public void whenLife360BidOutbidsPrebid_PrebidExpirationDoesNotApply() throws IllegalAccessException {
        setLife360BidResponse(life360BidResponse(null, 2.0));
        getBidRequesterListener().onFetchCompleted(prebidBidResponse(1, 1.0));
        getDisplayViewListener().onAdLoaded();

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        assertFalse(bannerView.isExpired());
        verify(mockBannerListener, never()).onAdExpired(bannerView);
    }

    @Test
    public void whenLife360BidOutbidsPrebid_AdExpiresByLife360Expiration() throws IllegalAccessException {
        setLife360BidResponse(life360BidResponse(1, 2.0));
        getBidRequesterListener().onFetchCompleted(prebidBidResponse(null, 1.0));
        getDisplayViewListener().onAdLoaded();

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        assertTrue(bannerView.isExpired());
        verify(mockBannerListener).onAdExpired(bannerView);
    }

    @Test
    public void whenPrebidErrorFallsBackToLife360_PreviousBidTimerDoesNotCarryOver() throws IllegalAccessException {
        receiveBid(2);
        getDisplayViewListener().onAdLoaded();

        setLife360BidResponse(life360BidResponse(null, 1.0));
        getBidRequesterListener().onError(new AdException(AdException.INTERNAL_ERROR, "Test"));
        getDisplayViewListener().onAdLoaded();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);

        assertFalse(bannerView.isExpired());
        verify(mockBannerListener, never()).onAdExpired(bannerView);
    }

    @Test
    public void whenNoBannerListener_LoadedAdStillExpires() {
        bannerView.setBannerListener(null);
        receiveBid(1);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        getDisplayViewListener().onAdLoaded();

        assertTrue(bannerView.isExpired());
    }

    private void receiveBid(Integer expirationTimeSeconds) {
        BidResponse mockBidResponse = mock(BidResponse.class);
        when(mockBidResponse.getExpirationTimeSeconds()).thenReturn(expirationTimeSeconds);
        getBidRequesterListener().onFetchCompleted(mockBidResponse);
        // The primary ad server has answered, so a new load is allowed again.
        changePrimaryAdServerRequestStatus(false);
    }

    private BidResponse prebidBidResponse(Integer expirationTimeSeconds, double price) {
        BidResponse response = mock(BidResponse.class);
        stubBid(response, expirationTimeSeconds, price);
        return response;
    }

    private Life360BidResponse life360BidResponse(Integer expirationTimeSeconds, double price) {
        Life360BidResponse response = mock(Life360BidResponse.class);
        stubBid(response, expirationTimeSeconds, price);
        return response;
    }

    private void stubBid(BidResponse response, Integer expirationTimeSeconds, double price) {
        Bid bid = mock(Bid.class);
        when(bid.getPrice()).thenReturn(price);
        when(response.getWinningBid()).thenReturn(bid);
        when(response.getExpirationTimeSeconds()).thenReturn(expirationTimeSeconds);
    }

    private void setLife360BidResponse(Life360BidResponse response) throws IllegalAccessException {
        NativoServerProxy nativoServer = (NativoServerProxy) WhiteBox.field(BannerView.class, "nativoServer").get(bannerView);
        // The setter is private: only a Life360 bid request is meant to populate it.
        WhiteBox.field(NativoServerProxy.class, "life360BidResponse").set(nativoServer, response);
    }
    //endregion ================= Bid expiration

    @Test
    public void whenDisplayViewOnAdDisplayed_CallBannerListenerOnAdDisplayedAndTrackImpression()
        throws IllegalAccessException {
        getDisplayViewListener().onAdDisplayed();
        verify(mockBannerListener).onAdDisplayed(bannerView);
        verify(mockEventHandler).trackImpression();
    }

    @Test
    public void whenDisplayViewOnAdFailed_CallBannerListenerOnAdFailed()
        throws IllegalAccessException {
        getDisplayViewListener().onAdFailed(new AdException(AdException.INTERNAL_ERROR, ""));
        verify(mockBannerListener).onAdFailed(eq(bannerView), any(AdException.class));
    }

    @Test
    public void whenDisplayViewOnAdOpened_CallBannerListenerOnAdClicked()
        throws IllegalAccessException {
        getDisplayViewListener().onAdClicked();
        verify(mockBannerListener).onAdClicked(bannerView);
    }

    @Test
    public void whenDisplayViewOnAdClosed_CallBannerListenerOnAdClosed()
        throws IllegalAccessException {
        getDisplayViewListener().onAdClosed();
        verify(mockBannerListener).onAdClosed(bannerView);
    }

    @Test
    public void whenDisplayVideoOnVideoCompleted_CallBannerVideoListenerOnVideoCompleted()
            throws IllegalAccessException {
        getDisplayVideoListener().onVideoCompleted();
        verify(mockBannerVideoListener).onVideoCompleted(bannerView);
    }

    @Test
    public void whenDisplayVideoOnVideoPaused_CallBannerVideoListenerOnVideoPaused()
            throws IllegalAccessException {
        getDisplayVideoListener().onVideoPaused();
        verify(mockBannerVideoListener).onVideoPaused(bannerView);
    }

    @Test
    public void whenDisplayVideoOnVideoResumed_CallBannerVideoListenerOnVideoResumed()
            throws IllegalAccessException {
        getDisplayVideoListener().onVideoResumed();
        verify(mockBannerVideoListener).onVideoResumed(bannerView);
    }

    @Test
    public void whenDisplayVideoOnVideoUnMuted_CallBannerVideoListenerOnVideoUnMuted()
            throws IllegalAccessException {
        getDisplayVideoListener().onVideoUnMuted();
        verify(mockBannerVideoListener).onVideoUnMuted(bannerView);
    }

    @Test
    public void whenDisplayVideoOnVideoMuted_CallBannerVideoListenerOnVideoMuted()
            throws IllegalAccessException {
        getDisplayVideoListener().onVideoMuted();
        verify(mockBannerVideoListener).onVideoMuted(bannerView);
    }

    @Test
    public void whenStopRefresh_BidLoaderCancelRefresh() {
        bannerView.stopRefresh();

        verify(mockBidLoader, times(1)).cancelRefresh();
    }

    @Test
    public void setAdPosition_EqualsGetAdPosition() {
        bannerView.setAdPosition(null);
        assertEquals(AdPosition.UNDEFINED, bannerView.getAdPosition());

        bannerView.setAdPosition(AdPosition.FOOTER);
        assertEquals(AdPosition.FOOTER, bannerView.getAdPosition());

        bannerView.setAdPosition(AdPosition.HEADER);
        assertEquals(AdPosition.HEADER, bannerView.getAdPosition());

        bannerView.setAdPosition(AdPosition.SIDEBAR);
        assertEquals(AdPosition.SIDEBAR, bannerView.getAdPosition());

        bannerView.setAdPosition(AdPosition.UNKNOWN);
        assertEquals(AdPosition.UNKNOWN, bannerView.getAdPosition());
    }

    @Test
    public void setPbAdSlot_EqualsGetPbAdSlot() {
        final String expected = "12345";
        bannerView.setPbAdSlot(expected);
        assertEquals(expected, bannerView.getPbAdSlot());
    }

    @Test
    public void setGpid_EqualsGetGpid() {
        final String expected = "/life360-ads/location/slot_1";
        bannerView.setGpid(expected);
        assertEquals(expected, bannerView.getGpid());
    }

    @Test
    public void gpid_defaultsToNull() {
        assertNull(bannerView.getGpid());
    }

    @Test
    public void setPrebidServerEnabled_EqualsIsPrebidServerEnabled() {
        bannerView.setPrebidServerEnabled(false);
        assertFalse(bannerView.isPrebidServerEnabled());

        bannerView.setPrebidServerEnabled(true);
        assertTrue(bannerView.isPrebidServerEnabled());
    }

    @Test
    public void prebidServerEnabled_defaultsToSdkWideValue() {
        Life360Ads.setPrebidServerEnabled(false);
        assertFalse(new BannerView(mockContext, AD_UNIT_ID, AD_SIZE).isPrebidServerEnabled());

        Life360Ads.setPrebidServerEnabled(true);
        assertTrue(new BannerView(mockContext, AD_UNIT_ID, AD_SIZE).isPrebidServerEnabled());
    }

    private BidRequesterListener getBidRequesterListener() {
        try {
            return (BidRequesterListener) WhiteBox.field(BannerView.class, "bidRequesterListener").get(bannerView);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private DisplayViewListener getDisplayViewListener() {
        try {
            return (DisplayViewListener) WhiteBox.field(BannerView.class, "displayViewListener").get(bannerView);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private DisplayVideoListener getDisplayVideoListener() {
        try {
            return (DisplayVideoListener) WhiteBox.field(BannerView.class, "displayVideoListener").get(bannerView);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private BannerEventListener getBannerEventListener() {
        try {
            return (BannerEventListener) WhiteBox.field(BannerView.class, "bannerEventListener").get(bannerView);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

}
