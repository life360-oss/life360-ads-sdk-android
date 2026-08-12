package com.life360.ads.renderer

import android.content.Context
import org.prebid.mobile.api.rendering.PrebidDisplayView
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.listeners.DisplayVideoListener
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener

/**
 * Life360-specific subclass of PrebidDisplayView that fixes the view removal issue
 * when scrolling past the banner view.
 */
class Life360DisplayView : PrebidDisplayView {

    constructor(
        context: Context,
        listener: DisplayViewListener,
        displayVideoListener: DisplayVideoListener?,
        adUnitConfiguration: AdUnitConfiguration,
        response: BidResponse
    ) : super(context, listener, displayVideoListener, adUnitConfiguration, response)

}