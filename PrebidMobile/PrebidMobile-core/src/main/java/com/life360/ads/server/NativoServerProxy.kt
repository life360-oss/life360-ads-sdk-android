package com.life360.ads.server

import com.life360.ads.bid.Life360BidResponse
import com.life360.ads.networking.Life360BidRequester
import org.prebid.mobile.LogUtil
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.exceptions.NoBidException
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse

typealias Life360BidResponseCallback = (bidResponse: Life360BidResponse?, shouldRenderImmediately: Boolean, error: AdException?) -> Unit

class NativoServerProxy {

    private val life360BidRequester = Life360BidRequester()
    var life360BidResponse : Life360BidResponse? = null
        private set

    fun requestLife360Bid(
        adUnitConfig: AdUnitConfiguration,
        callback: Life360BidResponseCallback
    ) {
        life360BidResponse = null
        life360BidRequester.requestBids(
            adUnitConfig
        ) { response: BidResponse?, error: AdException? ->
            life360BidResponse = response as Life360BidResponse?

            if (error != null && error !is NoBidException) {
                LogUtil.debug(this::class.simpleName, error.message)
            }
            if (response != null) {
                callback(response, life360BidRequester.shouldRenderImmediately(response), null)
            } else {
                callback(null, false, error)
            }
        }
    }

    fun decideWinner(
        prebidBidResponse: BidResponse?
    ): BidResponse? {
        if (prebidBidResponse == null) {
            return life360BidResponse
        }
        if (life360BidResponse == null) {
            return prebidBidResponse
        }
        val prebidPrice = getBidPrice(prebidBidResponse)
        val life360Price = getBidPrice(life360BidResponse)

        if (life360Price >= prebidPrice) {
            return life360BidResponse
        }
        return prebidBidResponse
    }

    fun getBidFromResponse(response: BidResponse?): Bid? {
        return response?.getWinningBid()
    }

    fun getBidPrice(response: BidResponse?): Double {
        val bid = getBidFromResponse(response)
        return bid?.price ?: 0.0
    }
}