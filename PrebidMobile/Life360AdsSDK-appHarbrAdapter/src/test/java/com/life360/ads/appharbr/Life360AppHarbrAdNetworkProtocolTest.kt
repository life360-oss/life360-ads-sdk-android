package com.life360.ads.appharbr

import android.content.Context
import android.widget.FrameLayout
import com.appharbr.sdk.engine.adformat.AdDataType
import com.appharbr.sdk.engine.adformat.AdFormat
import com.life360.ads.Life360Ads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.DisplayView
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.views.webview.WebViewBase
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class Life360AppHarbrAdNetworkProtocolTest {

    private val protocol = Life360AppHarbrAdNetworkProtocol

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** AppHarbr attributes scans to the Life360 product version, not the upstream Prebid version. */
    @Test
    fun adNetworkVersion_isTheLife360ProductVersion() {
        assertEquals(Life360Ads.version, protocol.adNetworkVersion)
        assertNotEquals(Life360Ads.prebidVersion, protocol.adNetworkVersion)
    }

    @Test
    fun adNetworkAdapterName_isNonNullForEveryFormat() {
        AdFormat.values().forEach { format ->
            assertNotNull(protocol.adNetworkAdapterName(format))
        }
    }

    /**
     * AppHarbr is not initialized in a unit test, so it is expected to refuse the registration. What
     * matters is that a refusal — or a missing adapter class — comes back as false rather than as an
     * exception thrown through the SDK.
     */
    @Test
    fun initAdQualityService_reportsFailureRatherThanThrowing() {
        assertFalse(protocol.initAdQualityService())
    }

    @Test
    fun getWinningBid_readsTheBannerViewItIsHanded() {
        val bannerView = bannerViewWinning(
            configId = "nativo-imp-id",
            creativeId = "crid-42",
            winningBidJson = """{"price":1.23}""",
            targeting = hashMapOf("hb_bidder" to "nativo"),
        )

        val properties = protocol.getWinningBid("mediation-unit", AdFormat.BANNER, bannerView)

        assertEquals("mediation-unit", properties.mediationAdUnitId)
        assertEquals("nativo-imp-id", properties.adNetworkUnitId)
        assertEquals("crid-42", properties.creativeId)
        assertEquals("""{"price":1.23}""", properties.content)
        assertEquals(AdDataType.JSON, properties.contentType)
        assertEquals(mapOf("hb_bidder" to "nativo"), properties.customTargetingMap)
    }

    /** A scan requested before a bid response exists must not report the literal string "null". */
    @Test
    fun getWinningBid_reportsEmptyContentWhenBidResponseIsMissing() {
        val bannerView = mock(BannerView::class.java)
        `when`(bannerView.bidResponse).thenReturn(null)

        val properties = protocol.getWinningBid("mediation-unit", AdFormat.BANNER, bannerView)

        assertEquals("", properties.content)
    }

    /** The SDK rendered ad view reports its own WebView, so no search through the hierarchy is needed. */
    @Test
    fun getWinningBid_takesTheWebViewFromTheRenderedAdView() {
        val webView = mock(WebViewBase::class.java)
        val displayView = mock(DisplayView::class.java)
        `when`(displayView.renderedWebView).thenReturn(webView)
        val bannerView = bannerViewWinning("config", "crid", winningBidJson = "{}", targeting = hashMapOf())
        `when`(bannerView.getChildAt(0)).thenReturn(displayView)

        val properties = protocol.getWinningBid("mediation-unit", AdFormat.BANNER, bannerView)

        assertSame(webView, properties.webViewRef)
    }

    /**
     * An ad server win replaces the SDK rendered view with the ad server's own, which reports no
     * WebView. Reporting none is right: scanning a WebView this SDK did not render is not our call.
     */
    @Test
    fun getWinningBid_reportsNoWebViewForAnAdServerRenderedView() {
        val bannerView = bannerViewWinning("config", "crid", winningBidJson = "{}", targeting = hashMapOf())
        `when`(bannerView.getChildAt(0)).thenReturn(FrameLayout(context))

        val properties = protocol.getWinningBid("mediation-unit", AdFormat.BANNER, bannerView)

        assertNull(properties.webViewRef)
    }

    /** A scan can arrive before the creative resolves, which has to report no WebView rather than throw. */
    @Test
    fun getWinningBid_reportsNoWebViewBeforeTheCreativeResolves() {
        val displayView = mock(DisplayView::class.java)
        `when`(displayView.renderedWebView).thenReturn(null)
        val bannerView = bannerViewWinning("config", "crid", winningBidJson = "{}", targeting = hashMapOf())
        `when`(bannerView.getChildAt(0)).thenReturn(displayView)

        val properties = protocol.getWinningBid("mediation-unit", AdFormat.BANNER, bannerView)

        assertNull(properties.webViewRef)
    }

    /**
     * AppHarbr hands back whatever the publisher registered with `addBannerView`, which may be a view it
     * wrapped around ours. That has to yield an empty bid rather than an exception, since a failed scan
     * must not break ad serving.
     */
    @Test
    fun getWinningBid_reportsAnEmptyBidForAnythingButABannerView() {
        val properties = protocol.getWinningBid("mediation-unit", AdFormat.BANNER, "not an ad view")

        assertEquals("mediation-unit", properties.mediationAdUnitId)
        assertEquals(AdFormat.BANNER, properties.adFormat)
        assertEquals(AdDataType.JSON, properties.contentType)
        assertEquals("", properties.content)
        assertEquals("", properties.creativeId)
        assertEquals("", properties.adNetworkUnitId)
        assertNull(properties.webViewRef)
        assertNull(properties.customTargetingMap)
    }

    /**
     * PREBID_LIFE360 is absent from AppHarbr's publicly published builds, so resolution falls back
     * instead of failing to link. Either outcome is valid depending on which AppHarbr build is on the
     * classpath — what matters is that the lookup never throws.
     */
    @Test
    fun getWinningBid_resolvesAnAdNetworkOnAnyAppHarbrBuild() {
        val properties = protocol.getWinningBid("mediation-unit", AdFormat.BANNER, "not an ad view")

        assertNotNull(properties.adNetwork)
    }

    private fun bannerViewWinning(
        configId: String,
        creativeId: String,
        winningBidJson: String,
        targeting: HashMap<String, String>,
    ): BannerView {
        val bid = mock(Bid::class.java)
        `when`(bid.crid).thenReturn(creativeId)

        val adUnitConfiguration = mock(AdUnitConfiguration::class.java)
        `when`(adUnitConfiguration.configId).thenReturn(configId)

        val bidResponse = mock(BidResponse::class.java)
        `when`(bidResponse.winningBid).thenReturn(bid)
        `when`(bidResponse.adUnitConfiguration).thenReturn(adUnitConfiguration)
        `when`(bidResponse.targeting).thenReturn(targeting)
        `when`(bidResponse.winningBidJson).thenReturn(winningBidJson)

        val bannerView = mock(BannerView::class.java)
        `when`(bannerView.bidResponse).thenReturn(bidResponse)
        return bannerView
    }
}
