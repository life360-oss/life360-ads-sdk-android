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
import android.view.View;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.bidding.data.bid.Bid;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.listeners.DisplayVideoListener;
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener;
import org.prebid.mobile.rendering.models.AdDetails;
import org.prebid.mobile.rendering.views.AdViewManager;
import org.prebid.mobile.rendering.views.AdViewManagerListener;
import org.prebid.mobile.rendering.views.webview.PrebidWebViewBase;
import org.prebid.mobile.rendering.views.webview.WebViewBanner;
import org.prebid.mobile.rendering.views.webview.WebViewBase;
import org.prebid.mobile.test.utils.WhiteBox;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 19)
public class PrebidDisplayViewTest {

    private PrebidDisplayView prebidDisplayView;
    private Context context;
    private AdUnitConfiguration adUnitConfiguration;
    @Mock private BidResponse bidResponse;
    @Mock private DisplayViewListener mockDisplayViewListener;
    @Mock private DisplayVideoListener mockDisplayVideoListener;
    @Mock private AdViewManager mockAdViewManager;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(PrebidDisplayViewTest.this);

        context = Robolectric.buildActivity(Activity.class).create().get();

        PrebidMobile.initializeSdk(context, "https://prebid-server-test-j.prebid.org/openrtb2/auction", null);

        adUnitConfiguration = new AdUnitConfiguration();
        adUnitConfiguration.setAdFormat(AdFormat.BANNER);

        BidResponse mockResponse = mock(BidResponse.class);
        Bid mockBid = mock(Bid.class);
        when(mockBid.getAdm()).thenReturn("adm");
        when(mockResponse.getWinningBid()).thenReturn(mockBid);

        prebidDisplayView = new PrebidDisplayView(context, mockDisplayViewListener, mockDisplayVideoListener, adUnitConfiguration, mockResponse);
        reset(mockDisplayViewListener);
    }

    @Test
    public void whenDisplayAd_LoadBidTransaction() {
        Assert.assertNotNull(WhiteBox.getInternalState(prebidDisplayView, "adViewManager"));
    }

    @Test
    public void whenAdViewManagerListenerAdLoaded_NotifyListenerOnAdLoaded()
        throws IllegalAccessException {
        AdViewManagerListener adViewManagerListener = getAdViewManagerListener();
        adViewManagerListener.adLoaded(mock(AdDetails.class));
        verify(mockDisplayViewListener).onAdLoaded();
    }

    @Test
    public void whenAdViewManagerListenerViewReadyForImmediateDisplay_NotifyListenerOnAdDisplayed()
        throws IllegalAccessException {
        AdViewManagerListener adViewManagerListener = getAdViewManagerListener();
        adViewManagerListener.viewReadyForImmediateDisplay(mock(View.class));
        verify(mockDisplayViewListener).onAdDisplayed();
    }

    @Test
    public void whenAdViewManagerListenerFailedToLoad_NotifyListenerOnAdFailed()
        throws IllegalAccessException {
        AdViewManagerListener adViewManagerListener = getAdViewManagerListener();
        adViewManagerListener.failedToLoad(new AdException(AdException.INTERNAL_ERROR, "Test"));
        verify(mockDisplayViewListener).onAdFailed(any(AdException.class));
    }

    @Test
    public void whenAdViewManagerListenerCreativeWasClicked_NotifyListenerOnAdClicked()
        throws IllegalAccessException {
        AdViewManagerListener adViewManagerListener = getAdViewManagerListener();
        adViewManagerListener.creativeClicked("");
        verify(mockDisplayViewListener).onAdClicked();
    }

    @Test
    public void whenAdViewManagerListenerCreativeInterstitialDidClose_NotifyListenerOnAdClosed()
        throws IllegalAccessException {
        AdViewManagerListener adViewManagerListener = getAdViewManagerListener();
        adViewManagerListener.creativeInterstitialClosed();
        verify(mockDisplayViewListener).onAdClosed();
    }

    @Test
    public void whenAdViewManagerListenerCreativeDidCollapse_NotifyListenerOnAdClosed()
        throws IllegalAccessException {
        AdViewManagerListener adViewManagerListener = getAdViewManagerListener();
        adViewManagerListener.creativeCollapsed();
        verify(mockDisplayViewListener).onAdClosed();
    }

    @Test
    public void whenNoCreativeResolved_GetRenderedWebViewReturnsNull() {
        useMockAdViewManager();
        when(mockAdViewManager.getCurrentCreativeView()).thenReturn(null);

        Assert.assertNull(prebidDisplayView.getRenderedWebView());
    }

    @Test
    public void whenCreativeResolvedButNotDisplayed_GetRenderedWebViewReturnsCreativeWebView() {
        WebViewBase mockWebView = mockResolvedCreative().getWebView();

        Assert.assertEquals(-1, prebidDisplayView.indexOfChild(mockWebView));
        Assert.assertSame(mockWebView, prebidDisplayView.getRenderedWebView());
    }

    @Test
    public void whenTwoPartCreativeResolved_GetRenderedWebViewReturnsMraidWebView() {
        PrebidWebViewBase mockCreativeView = mock(PrebidWebViewBase.class);
        WebViewBanner mockMraidWebView = mock(WebViewBanner.class);
        when(mockCreativeView.getWebView()).thenReturn(null);
        when(mockCreativeView.getMraidWebView()).thenReturn(mockMraidWebView);
        useMockAdViewManager();
        when(mockAdViewManager.getCurrentCreativeView()).thenReturn(mockCreativeView);

        Assert.assertSame(mockMraidWebView, prebidDisplayView.getRenderedWebView());
    }

    @Test
    public void whenNonHtmlCreativeResolved_GetRenderedWebViewReturnsNull() {
        useMockAdViewManager();
        when(mockAdViewManager.getCurrentCreativeView()).thenReturn(new View(context));

        Assert.assertNull(prebidDisplayView.getRenderedWebView());
    }

    @Test
    public void whenDestroyed_GetRenderedWebViewReturnsNull() {
        mockResolvedCreative();

        prebidDisplayView.destroy();

        Assert.assertNull(prebidDisplayView.getRenderedWebView());
    }

    /** Stands in a resolved one part HTML creative, as it exists between ad load and display. */
    private PrebidWebViewBase mockResolvedCreative() {
        PrebidWebViewBase mockCreativeView = mock(PrebidWebViewBase.class);
        when(mockCreativeView.getWebView()).thenReturn(mock(WebViewBase.class));
        useMockAdViewManager();
        when(mockAdViewManager.getCurrentCreativeView()).thenReturn(mockCreativeView);
        return mockCreativeView;
    }

    private void useMockAdViewManager() {
        WhiteBox.setInternalState(prebidDisplayView, "adViewManager", mockAdViewManager);
    }

    private AdViewManagerListener getAdViewManagerListener() throws IllegalAccessException {
        return (AdViewManagerListener) WhiteBox.field(PrebidDisplayView.class, "adViewManagerListener").get(prebidDisplayView);
    }

}