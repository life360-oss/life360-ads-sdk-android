package com.life360.demo

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.life360.ads.networking.Life360QueryParameterStore
import org.prebid.mobile.AdSize
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.listeners.Life360BannerViewListener

/**
 * Owns the L360 Video tab's [BannerView] for the lifetime of the Activity.
 *
 * The view is created and destroyed here rather than inside composition because switching tabs tears
 * the Compose subtree down. A view created by an `AndroidView` factory would be rebuilt on every
 * visit, which would issue a fresh bid request and start a new Open Measurement session each time —
 * making the impression counts this harness exists to check meaningless.
 */
@Stable
class Life360VideoAdSlotController(
    private val activity: Activity,
) : AdSlotController {
    override val config = AdConfiguration.VIDEO
    override val events = AdEventLog()

    override var state: AdSlotState by mutableStateOf(AdSlotState.Idle)
        private set
    val configId: String = "nativo-video-tout-imp-id"
    val requestAdSize: AdSize = AdSize(300, 250)

    var bannerView: BannerView? by mutableStateOf(null)
        private set

    override fun load() {
        if (bannerView != null) return

        events.reset()
        state = AdSlotState.Loading

        applyCustomQueryParams()

        val banner = BannerView(activity, configId, requestAdSize).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBannerListener(bannerListener)
        }
        bannerView = banner
        banner.loadAd()
    }

    override fun reload() {
        destroy()
        load()
    }

    override fun destroy() {
        bannerView?.let { banner ->
            (banner.parent as? ViewGroup)?.removeView(banner)
            banner.destroy()
        }
        bannerView = null
        state = AdSlotState.Idle
    }

    private fun applyCustomQueryParams() {
        val prefs = activity.getSharedPreferences(
            Life360QueryParameterStore.prefsName(configId),
            Context.MODE_PRIVATE,
        )
        prefs.edit()
            .clear()
            .putString("ntv_a", "442149")
            .putString("ntv_tm", "tout")
            .apply()
    }

    private val bannerListener = object : Life360BannerViewListener {
        override fun onAdLoaded(bannerView: BannerView) {
            record("onAdLoaded")
            state = loadedState(bannerView)
        }

        // Fires instead of onAdLoaded when Life360 both wins and renders the creative itself.
        override fun onLife360AdLoaded(bannerView: BannerView) {
            record("onLife360AdLoaded")
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

    private fun loadedState(bannerView: BannerView): AdSlotState.Loaded {
        val response = bannerView.bidResponse
        return AdSlotState.Loaded
    }

    private fun record(name: String, detail: String? = null) {
        Log.d(TAG, "[${config.title}] $name${detail?.let { " — $it" } ?: ""}")
        events.record(name, detail)
    }

    private companion object {
        const val TAG = "Life360AdsDemo"
    }
}
