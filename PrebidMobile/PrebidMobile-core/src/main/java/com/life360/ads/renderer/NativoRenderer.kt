package com.life360.ads.renderer

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import com.life360.ads.bid.NativoAdType
import com.life360.ads.bid.NativoBidExt
import com.life360.ads.utils.NativoUtils
import org.json.JSONObject
import org.prebid.mobile.LogUtil
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.PrebidDisplayView
import org.prebid.mobile.api.rendering.PrebidMobileInterstitialControllerInterface
import org.prebid.mobile.api.rendering.pluginrenderer.PluginEventListener
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRenderer
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.display.InterstitialController
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialControllerListener
import org.prebid.mobile.rendering.bidding.listeners.DisplayVideoListener
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener

class NativoRenderer : PrebidMobilePluginRenderer {

    companion object Companion {
        private const val TAG = "NativoRenderer"
        const val NAME = "NativoRenderer"
        const val VERSION = "1.0.0"
    }

    override fun getName(): String = NAME

    override fun getVersion(): String = VERSION

    override fun getData(): JSONObject? = null

    override fun createBannerAdView(
        context: Context,
        displayViewListener: DisplayViewListener,
        displayVideoListener: DisplayVideoListener?,
        adUnitConfiguration: AdUnitConfiguration,
        bidResponse: BidResponse
    ): View {
        var displayViewRef: PrebidDisplayView? = null

        val forwardingListener = object : DisplayViewListener {
            override fun onAdLoaded() = displayViewListener.onAdLoaded()
            override fun onAdClicked() {
                displayViewRef?.let { displayView ->
                    val snapshot = NativoUtils.captureViewSnapshot(displayView)
                    snapshot.tag = TAG
                    snapshot.layoutParams = FrameLayout.LayoutParams(
                        MATCH_PARENT,
                        MATCH_PARENT
                    )
                    displayView.addView(snapshot)
                }
                displayViewListener.onAdClicked()
            }
            override fun onAdClosed() {
                displayViewListener.onAdClosed()
                displayViewRef?.let { displayView ->
                    displayView.postDelayed({
                        val snapshot = displayView.findViewWithTag<ImageView>(TAG)
                        snapshot?.let {
                            displayView.removeView(it)
                        }
                    }, 500)
                }
            }
            override fun onAdDisplayed() {
                displayViewRef?.let { displayView ->
                    if (isNativoAd(bidResponse)) {
                        renderNativoAd(displayView, bidResponse)
                    }
                    displayViewListener.onAdDisplayed()
                }
            }
            override fun onAdFailed(exception: AdException?) = displayViewListener.onAdFailed(exception)
        }

        displayViewRef = PrebidDisplayView(
            context,
            forwardingListener,
            displayVideoListener,
            adUnitConfiguration,
            bidResponse
        )

        val adType = bidResponse.winningBid?.let { NativoBidExt.getNativoAdType(it) }
        if (adType == NativoAdType.STORY || adType == NativoAdType.STP_VIDEO || adType == NativoAdType.CTP_VIDEO) {
            displayViewRef.getInterstitialDisplayProperties().dialogBackgroundColor = Color.BLACK
        }

        // Set default height to WRAP_CONTENT for non-Nativo ads
        displayViewRef.layoutParams = FrameLayout.LayoutParams(
            MATCH_PARENT,
            WRAP_CONTENT
        )

        return displayViewRef
    }

    override fun createInterstitialController(
        context: Context,
        interstitialControllerListener: InterstitialControllerListener,
        adUnitConfiguration: AdUnitConfiguration,
        bidResponse: BidResponse
    ): PrebidMobileInterstitialControllerInterface? {
        return try {
            InterstitialController(context, interstitialControllerListener)
        } catch (exception: AdException) {
            LogUtil.error(TAG, "message: ${exception.message}")
            null
        }
    }

    override fun isSupportRenderingFor(adUnitConfiguration: AdUnitConfiguration): Boolean {
        return adUnitConfiguration.isAdType(AdFormat.BANNER)
    }

    override fun registerEventListener(
        pluginEventListener: PluginEventListener,
        listenerKey: String
    ) {
    }

    override fun unregisterEventListener(listenerKey: String) {
    }

    override fun didInjectView(view: View, inBannerView: View, bidResponse: BidResponse) {
        // Set wrap content as the default layout. Will be overridden later if is a Nativo ad.
        view.layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
    }

    // Private methods

    private fun isNativoAd(bidResponse: BidResponse): Boolean {
        bidResponse.winningBid?.let {
            val nativoAdType = NativoBidExt.getNativoAdType(it)
            if (nativoAdType != null) {
                return nativoAdType != NativoAdType.STANDARD_DISPLAY
            } else {
                // Fallback
                val adm = bidResponse.winningBid?.adm ?: ""
                return adm.contains("load.js", ignoreCase = true)
            }
        }
        return false
    }

    /**
     * Expand the article full width, and attempt to expand height to parent container,
     * while also ensuring at least a minimum height of the requested bid.
     */
    private fun renderNativoAd(displayView: PrebidDisplayView, bidResponse: BidResponse) {
        // Wait for attachment and layout before rendering
        NativoUtils.runOnLaidOut(displayView) {
            val inBannerView = generateSequence(displayView.parent) { it.parent }
                .filterIsInstance<BannerView>()
                .firstOrNull()
            if (inBannerView == null) {
                LogUtil.error(TAG, "Nativo renderer expected a parent BannerView, but none was found.")
                return@runOnLaidOut
            }
            val parentView = (inBannerView.parent as? View) ?: inBannerView
            val minHeightPx = (bidResponse.winningBid?.height ?: 0).dpToPx()
            expandFullWidth(displayView, inBannerView)
            applyHeightStrategy(displayView, inBannerView, parentView, minHeightPx)
        }
    }

    /**
     * Chooses between two height strategies:
     * - MATCH_PARENT or WRAP + minimum
     */
    private fun applyHeightStrategy(displayView: PrebidDisplayView, boundaryView: View, parentView: View, minHeightPx: Int) {
        val parentHeight = if (parentView.layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT)  0 else parentView.height
        if (parentHeight >= minHeightPx) {
            // If parent is taller than minHeight, expand to match parent
            expandFullHeight(displayView, boundaryView)
            expandChildViews(displayView, minHeightPx, useMatchParent = true)
        } else {
            // If parent is less than minHeight, enforce the bid's minimum
            setMinHeightChainUpward(displayView, boundaryView, minHeightPx)
            expandChildViews(displayView, minHeightPx, useMatchParent = false)
        }
    }

    private fun expandFullWidth(view: View, boundaryView: View) {
        var currentView: View? = view
        while (currentView != null) {
            val params = currentView.layoutParams
            if (params != null) {
                params.width = MATCH_PARENT
                currentView.layoutParams = params
            }
            if (currentView == boundaryView) break
            currentView = (currentView.parent as? View)
        }
    }

    private fun expandFullHeight(view: View, boundaryView: View) {
        var currentView: View? = view
        while (currentView != null) {
            val params = currentView.layoutParams
            if (params != null) {
                params.height = MATCH_PARENT
                currentView.layoutParams = params
            }
            if (currentView == boundaryView) break
            currentView = (currentView.parent as? View)
        }
    }

    private fun setMinHeightChainUpward(view: View, boundaryView: View, minHeightPx: Int) {
        var currentView: View? = view
        while (currentView != null) {
            currentView.minimumHeight = minHeightPx
            val params = currentView.layoutParams
            if (params != null) {
                params.height = WRAP_CONTENT
                currentView.layoutParams = params
            }
            if (currentView == boundaryView) break
            currentView = (currentView.parent as? View)
        }
    }

    private fun expandChildViews(displayView: ViewGroup, minHeightPx: Int, useMatchParent: Boolean) {
        val firstChild = displayView.getChildAt(0)
        if (firstChild == null) {
            LogUtil.error(TAG, "Nativo renderer expected a child view on PrebidDisplayView, but none was found.")
            return
        }

        val heightParam = if (useMatchParent) MATCH_PARENT else minHeightPx

        var current = firstChild as? ViewGroup
        while (current != null) {
            val params = current.layoutParams
            if (params != null) {
                params.width = MATCH_PARENT
                params.height = heightParam
                current.layoutParams = params
            } else {
                current.layoutParams = FrameLayout.LayoutParams(
                    MATCH_PARENT,
                    heightParam
                )
            }

            current = current.getChildAt(0) as? ViewGroup
        }
    }
}

private fun Int.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}
