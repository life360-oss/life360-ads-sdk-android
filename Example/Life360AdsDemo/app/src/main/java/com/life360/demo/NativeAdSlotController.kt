package com.life360.demo

import android.app.Activity
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.prebid.mobile.NativeAdUnit
import org.prebid.mobile.NativeAsset
import org.prebid.mobile.NativeDataAsset
import org.prebid.mobile.NativeEventTracker
import org.prebid.mobile.NativeImageAsset
import org.prebid.mobile.NativeTitleAsset
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.PrebidNativeAd
import org.prebid.mobile.PrebidNativeAdEventListener
import org.prebid.mobile.ResultCode


@Stable
class NativeAdSlotController(private val activity: Activity) : AdSlotController {
    override val config = TabConfiguration.NATIVE

    private val configId = "test-imp-id-native"

    override var state: AdSlotState by mutableStateOf(AdSlotState.Idle)
        private set

    /** Retained for the lifetime of the slot rather than rebuilt per tab visit — see
     * [BannerAdSlotController]'s `bannerView` for why: dropping it would stop the SDK's viewability
     * timer and detach its click handler. */
    var contentView: NativeAdContentView? by mutableStateOf(null)
        private set

    override fun load() {
        if (contentView != null) return

        state = AdSlotState.Loading

        val unit = NativeAdUnit(configId).apply {
            setContextType(NativeAdUnit.CONTEXT_TYPE.SOCIAL_CENTRIC)
            setPlacementType(NativeAdUnit.PLACEMENTTYPE.CONTENT_FEED)
            setPlacementCount(1)
            requestedAssets.forEach(::addAsset)
            addEventTracker(
                NativeEventTracker(
                    NativeEventTracker.EVENT_TYPE.IMPRESSION,
                    arrayListOf(
                        NativeEventTracker.EVENT_TRACKING_METHOD.IMAGE,
                        NativeEventTracker.EVENT_TRACKING_METHOD.JS,
                    ),
                ),
            )
        }

        // Allow winning bid to be used without a cache id
        PrebidMobile.setUseCacheForReportingWithRenderingApi(false)

        unit.fetchDemand { bidInfo ->
            if (bidInfo.resultCode != ResultCode.SUCCESS) {
                state = AdSlotState.Failed(bidInfo.resultCode.name)
                return@fetchDemand
            }

            val ad = bidInfo.nativeCacheId?.let(PrebidNativeAd::create)
            if (ad == null) {
                state = AdSlotState.Failed("no renderable native ad")
                return@fetchDemand
            }

            render(ad)
        }
    }

    override fun reload() {
        destroy()
        load()
    }

    override fun destroy() {
        contentView = null
        state = AdSlotState.Idle
    }

    private fun render(ad: PrebidNativeAd) {
        val view = NativeAdContentView(activity)
        view.bind(ad)
        contentView = view
        state = AdSlotState.Loaded
    }

    private val nativeAdEventListener = object : PrebidNativeAdEventListener {
        override fun onAdClicked() {}
        override fun onAdImpression() {}
        override fun onAdExpired() {}
    }

    /** Assets the demo asks for. All optional except the title so a partial-asset bid still renders
     * something rather than being discarded — matches the iOS counterpart's `requestedAssets`. */
    private val requestedAssets: List<NativeAsset>
        get() = listOf(
            NativeTitleAsset().apply { setLength(90); setRequired(true) },
            NativeImageAsset(50, 50).apply { setImageType(NativeImageAsset.IMAGE_TYPE.ICON); setRequired(false) },
            NativeImageAsset(300, 200).apply { setImageType(NativeImageAsset.IMAGE_TYPE.MAIN); setRequired(false) },
            NativeDataAsset().apply { setDataType(NativeDataAsset.DATA_TYPE.SPONSORED); setRequired(false) },
            NativeDataAsset().apply { setDataType(NativeDataAsset.DATA_TYPE.DESC); setRequired(false) },
            NativeDataAsset().apply { setDataType(NativeDataAsset.DATA_TYPE.CTATEXT); setRequired(false) },
        )

    private companion object {
        const val TAG = "Life360AdsDemo"
    }
}
