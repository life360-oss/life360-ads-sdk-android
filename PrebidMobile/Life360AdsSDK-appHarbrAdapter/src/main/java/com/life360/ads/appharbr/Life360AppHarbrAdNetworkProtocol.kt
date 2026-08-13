package com.life360.ads.appharbr

import android.view.ViewGroup
import com.appharbr.adapter.life360.AppHarbrLife360Adapter
import com.appharbr.sdk.adapter.AdQualityAdNetworkProperties
import com.appharbr.sdk.adapter.AdQualityAdNetworkProtocol
import com.appharbr.sdk.engine.AdSdk
import com.appharbr.sdk.engine.adformat.AdDataType
import com.appharbr.sdk.engine.adformat.AdFormat
import com.life360.ads.Life360Ads
import org.prebid.mobile.LogUtil
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.WebViewProvider

/**
 * Describes Life360 demand to AppHarbr so it can scan what the SDK renders. Register it from your app,
 * before `AppHarbr.initialize` and before the first ad loads:
 *
 * ```
 * Life360AppHarbrAdNetworkProtocol.initAdQualityService()
 * ```
 */
object Life360AppHarbrAdNetworkProtocol : AdQualityAdNetworkProtocol {

    private val TAG = "Life360AppHarbr"

    override val adNetworkVersion: String = Life360Ads.version

    /**
     * Registers this protocol with AppHarbr as the Life360 custom ad network.
     *
     * @return whether AppHarbr accepted the registration.
     */
    @JvmStatic
    fun initAdQualityService(): Boolean = try {
        AppHarbrLife360Adapter.initAdQualityService(this)
    } catch (throwable: Throwable) {
        LogUtil.error(
            TAG,
            "Could not register Life360 ad quality with AppHarbr; is AH-Life360-Adapter-*.aar on the " +
                    "classpath? $throwable"
        )
        false
    }

    override fun adNetworkAdapterName(adFormat: AdFormat): String = "Life360AdsSDK-appHarbrAdapter"

    override fun getWinningBid(
        mediationAdUnitId: String,
        adFormat: AdFormat,
        mediationObject: Any,
    ): AdQualityAdNetworkProperties {
        try {
            val bannerView = mediationObject as? BannerView
            if (bannerView == null) {
                LogUtil.warning(
                    TAG,
                    "Ad-quality scan requested for something other than a BannerView " +
                            "($mediationObject); reporting an empty bid to AppHarbr"
                )
            }
            val bidResponse = bannerView?.bidResponse

            /**
             * Created [WebViewProvider] specifically for AppHarbr as a way to get direct access
             * to the web view before it is injected into view
             */
            val webViewProvider = (bannerView as? ViewGroup)?.getChildAt(0) as? WebViewProvider
            if (webViewProvider == null) {
                LogUtil.debug(TAG, "Unable to find WebViewProvider from BannerView")
            }

            return AdQualityAdNetworkProperties(
                mediationAdUnitId,
                adFormat,
                AdSdk.PREBID_LIFE360,
                bidResponse?.adUnitConfiguration?.configId.orEmpty(),
                AdDataType.JSON, // the winning bid is handed over as its JSON representation
                bidResponse?.winningBidJson.orEmpty(),
                bidResponse?.winningBid?.crid.orEmpty(),
                webViewProvider?.renderedWebView,
                bidResponse?.targeting?.takeIf { it.isNotEmpty() },
            )
        } catch (throwable: Throwable) {
            LogUtil.error(TAG, "Failed to get winning bid: $throwable")
            return AdQualityAdNetworkProperties(
                mediationAdUnitId = mediationAdUnitId,
                adFormat = adFormat,
                adNetwork = AdSdk.CUSTOM,
                adNetworkUnitId = "",
                contentType = AdDataType.UNKNOWN,
                content = "",
                creativeId = ""
            )
        }
    }

    override fun onAdBlockedCleanCache(
        mediationAdUnitId: String,
        adFormat: AdFormat,
        mediationObject: Any,
        adNetworkUnitId: String,
        creativeId: String,
    ) {
        // Nothing to release: the SDK caches no creative of its own, and the WebView the bid rendered
        // into is torn down with the ad view it belongs to.
        LogUtil.debug(TAG, "AppHarbr blocked creative $creativeId on ad unit $adNetworkUnitId")
    }
}
