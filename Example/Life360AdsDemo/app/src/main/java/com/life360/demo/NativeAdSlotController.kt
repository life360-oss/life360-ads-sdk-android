package com.life360.demo

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.life360.ads.NativeAdUnit
import com.life360.ads.NativeAsset
import com.life360.ads.NativeDataAsset
import com.life360.ads.NativeEventTracker
import com.life360.ads.NativeImageAsset
import com.life360.ads.NativeTitleAsset
import com.life360.ads.PrebidMobile
import com.life360.ads.PrebidNativeAd
import com.life360.ads.PrebidNativeAdEventListener
import com.life360.ads.ResultCode

// Stored impression on the Prebid Server the app initialized against — the same id the iOS
// counterpart's NativeAdSlotView uses. Unlike Banner and L360 Video, this slot depends on it to
// fill: the original API is Prebid-Server-only, so an id the server doesn't recognize surfaces as an
// error rather than falling back to other demand.
private const val CONFIG_ID = "test-imp-id-native"

/**
 * A native ad slot — the one format in this harness that doesn't render through `BannerView`.
 *
 * Native uses Prebid's original API: `NativeAdUnit.fetchDemand` returns a cached bid rather than a
 * rendered creative, and the app builds the layout ([NativeAdContentView]) and registers it for
 * viewability itself. That mirrors the iOS counterpart's `NativeAdSlotView`, and it's what makes this
 * slot's demand — and so its ad — comparable to iOS's rather than to Banner/Video's BannerView path.
 */
@Stable
class NativeAdSlotController(private val activity: Activity) : AdSlotController {
    override val config = AdConfiguration.NATIVE
    override val events = AdEventLog()

    override var state: AdSlotState by mutableStateOf(AdSlotState.Idle)
        private set

    /** Retained for the lifetime of the slot rather than rebuilt per tab visit — see
     * [BannerAdSlotController]'s `bannerView` for why: dropping it would stop the SDK's viewability
     * timer and detach its click handler. */
    var contentView: NativeAdContentView? by mutableStateOf(null)
        private set

    override fun load() {
        if (contentView != null) return

        events.reset()
        state = AdSlotState.Loading

        val unit = NativeAdUnit(CONFIG_ID).apply {
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

        // Matches the iOS counterpart's NativeAdSlotView: without this, a bid the dev Prebid
        // Server didn't get to cache (e.g. no external Prebid Cache reachable) never counts as
        // "winning," even though its content is otherwise perfectly good.
        PrebidMobile.setUseCacheForReportingWithRenderingApi(false)

        record("fetchDemand sent", CONFIG_ID)
        unit.fetchDemand { bidInfo ->
            if (bidInfo.resultCode != ResultCode.SUCCESS) {
                record("fetchDemand failed", bidInfo.resultCode.name)
                state = AdSlotState.Failed(bidInfo.resultCode.name)
                return@fetchDemand
            }

            val ad = bidInfo.nativeCacheId?.let(PrebidNativeAd::create)
            if (ad == null) {
                record("fetchDemand succeeded but returned no renderable native ad")
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

        val registered = ad.registerView(view, view.clickableViews, nativeAdEventListener)
        record("rendered — registerView ${if (registered) "succeeded" else "failed"}")

        contentView = view
        // PrebidNativeAd.getSponsoredBy() returns "" rather than null when the bid didn't include it.
        val demandSource = ad.sponsoredBy.takeIf { it.isNotEmpty() } ?: "Native"
        state = AdSlotState.Loaded
    }

    private val nativeAdEventListener = object : PrebidNativeAdEventListener {
        override fun onAdClicked() = record("onAdClicked")
        override fun onAdImpression() = record("onAdImpression — viewable for 1s, trackers fired")
        override fun onAdExpired() = record("onAdExpired — cached bid timed out")
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

    private fun record(name: String, detail: String? = null) {
        Log.d(TAG, "[${config.title}] $name${detail?.let { " — $it" } ?: ""}")
        events.record(name, detail)
    }

    private companion object {
        const val TAG = "Life360AdsDemo"
    }
}
