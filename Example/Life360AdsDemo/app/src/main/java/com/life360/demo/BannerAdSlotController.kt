package com.life360.demo

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.life360.ads.api.exceptions.AdException
import com.life360.ads.api.rendering.BannerView
import com.life360.ads.api.rendering.listeners.Life360BannerViewListener
import com.life360.ads.networking.Life360QueryParameterStore

/** What the slot has to show, published to Compose. */
sealed interface AdSlotState {
    object Idle : AdSlotState
    object Loading : AdSlotState
    object Loaded : AdSlotState
    data class Failed(val message: String?) : AdSlotState
}

/** Owns one tab's ad request for the lifetime of the Activity — see [BannerAdSlotController] and
 * [NativeAdSlotController], the two shapes a request takes in this harness. */
interface AdSlotController {
    val config: AdConfiguration
    val events: AdEventLog
    val state: AdSlotState

    /** Requests an ad unless this slot already has one. Safe to call on every tab visit. */
    fun load()

    /** Tears the slot down and re-requests, so a scroll-tracking run can be repeated cleanly. */
    fun reload()

    fun destroy()
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
class BannerAdSlotController(
    override val config: AdConfiguration,
    private val activity: Activity,
) : AdSlotController {
    // Only Banner and L360 Video are ever wrapped in this controller — see AdConfiguration's kdoc —
    // so a missing configId/adSize here means a NATIVE-shaped config was passed in by mistake.
    private val configId = requireNotNull(config.configId) { "${config.title} has no configId" }
    private val adSize = requireNotNull(config.adSize) { "${config.title} has no adSize" }

    override val events = AdEventLog()

    override var state: AdSlotState by mutableStateOf(AdSlotState.Idle)
        private set

    var bannerView: BannerView? by mutableStateOf(null)
        private set

    override fun load() {
        if (bannerView != null) return

        events.reset()
        state = AdSlotState.Loading

        applyCustomQueryParams()

        val banner = BannerView(activity, configId, adSize).apply {
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
        if (config.customQueryParams.isEmpty()) return
        val prefs = activity.getSharedPreferences(
            Life360QueryParameterStore.prefsName(configId),
            Context.MODE_PRIVATE,
        )
        prefs.edit()
            .clear()
            .apply {
            config.customQueryParams.forEach { (key, value) -> putString(key, value) }
        }.apply()
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
