package com.life360.demo

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.life360.ads.api.exceptions.AdException
import com.life360.ads.api.rendering.BannerView
import com.life360.ads.api.rendering.listeners.BannerVideoListener
import com.life360.ads.api.rendering.listeners.NativoBannerViewListener

/** What the slot has to show, published to Compose. */
sealed interface AdSlotState {
    data object Idle : AdSlotState
    data object Loading : AdSlotState
    data class Loaded(val demandSource: String, val creativeSize: String?) : AdSlotState
    data class Failed(val message: String?) : AdSlotState
}

/**
 * Owns one format's [BannerView] for the lifetime of the Activity.
 *
 * The view is created and destroyed here rather than inside composition because switching tabs tears
 * the Compose subtree down. A view created by an `AndroidView` factory would be rebuilt on every
 * visit, which would issue a fresh bid request and start a new Open Measurement session each time —
 * making the impression counts this harness exists to check meaningless.
 */
@Stable
class AdSlotController(
    val format: DemoAdFormat,
    private val activity: Activity,
) {
    val events = AdEventLog()

    var state: AdSlotState by mutableStateOf(AdSlotState.Idle)
        private set

    var bannerView: BannerView? by mutableStateOf(null)
        private set

    /** Requests an ad unless this slot already has one. Safe to call on every tab visit. */
    fun load() {
        if (bannerView != null) return

        events.reset()
        state = AdSlotState.Loading

        val banner = BannerView(activity, format.configId, format.adSize).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBannerListener(bannerListener)
            // Attached to every slot, not just the video one: placements are configured server-side,
            // so a display slot serving a Nativo video creative is exactly the surprise worth seeing.
            setBannerVideoListener(videoListener)
        }
        bannerView = banner
        banner.loadAd()
    }

    /** Tears the slot down and re-requests, so a scroll-tracking run can be repeated cleanly. */
    fun reload() {
        destroy()
        load()
    }

    fun destroy() {
        bannerView?.let { banner ->
            (banner.parent as? ViewGroup)?.removeView(banner)
            banner.destroy()
        }
        bannerView = null
        state = AdSlotState.Idle
    }

    private val bannerListener = object : NativoBannerViewListener {
        override fun onAdLoaded(bannerView: BannerView) {
            record("onAdLoaded")
            state = loadedState(bannerView)
        }

        // Fires instead of onAdLoaded when Nativo both wins and renders the creative itself.
        override fun onNativoAdLoaded(bannerView: BannerView) {
            record("onNativoAdLoaded")
            state = loadedState(bannerView)
        }

        override fun onAdDisplayed(bannerView: BannerView) = record("onAdDisplayed")

        override fun onAdFailed(bannerView: BannerView, exception: AdException) {
            record("onAdFailed", exception.message)
            state = AdSlotState.Failed(exception.message)
        }

        override fun onAdClicked(bannerView: BannerView) = record("onAdClicked")

        override fun onAdClosed(bannerView: BannerView) = record("onAdClosed")
    }

    // onVideoPaused/onVideoResumed are driven by the SDK's visibility constraints, so scrolling the
    // slot out of and back into the viewport is what these two are here to prove.
    private val videoListener = object : BannerVideoListener {
        override fun onVideoCompleted(bannerView: BannerView) = record("onVideoCompleted")
        override fun onVideoPaused(bannerView: BannerView) = record("onVideoPaused", "left viewport")
        override fun onVideoResumed(bannerView: BannerView) = record("onVideoResumed", "back in viewport")
        override fun onVideoMuted(bannerView: BannerView) = record("onVideoMuted")
        override fun onVideoUnMuted(bannerView: BannerView) = record("onVideoUnMuted")
    }

    private fun loadedState(bannerView: BannerView): AdSlotState.Loaded {
        val response = bannerView.bidResponse
        return AdSlotState.Loaded(
            demandSource = NativoBidInspector.describeDemandSource(response),
            creativeSize = NativoBidInspector.describeCreativeSize(response),
        )
    }

    private fun record(name: String, detail: String? = null) {
        Log.d(TAG, "[${format.title}] $name${detail?.let { " — $it" } ?: ""}")
        events.record(name, detail)
    }

    private companion object {
        const val TAG = "Life360AdsDemo"
    }
}
