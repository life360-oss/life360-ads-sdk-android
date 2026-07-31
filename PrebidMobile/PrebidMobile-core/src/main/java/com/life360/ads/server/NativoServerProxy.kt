package com.life360.ads.server

import com.life360.ads.bid.NativoBidResponse
import com.life360.ads.networking.NativoBidRequester
import org.prebid.mobile.LogUtil
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.exceptions.NoBidException
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse

typealias NativoBidResponseCallback = (bidResponse: NativoBidResponse?, shouldRenderImmediately: Boolean, error: AdException?) -> Unit

class NativoServerProxy @JvmOverloads constructor(
    private val nativoBidRequester: NativoBidRequester = NativoBidRequester()
) {

    var nativoBidResponse : NativoBidResponse? = null
        private set

    fun requestNativoBid(
        adUnitConfig: AdUnitConfiguration,
        callback: NativoBidResponseCallback
    ) {
        nativoBidResponse = null
        nativoBidRequester.requestBids(
            adUnitConfig
        ) { response: BidResponse?, error: AdException? ->
            nativoBidResponse = response as NativoBidResponse?

            if (error != null && error !is NoBidException) {
                LogUtil.debug(this::class.simpleName, error.message)
            }
            if (response != null) {
                callback(response, nativoBidRequester.shouldRenderImmediately(response), null)
            } else {
                callback(null, false, error)
            }
        }
    }

    /**
     * Cancels any in-flight Nativo request and clears the retained bid. Owners must call this from their own
     * teardown, otherwise the Nativo leg outlives them and a stale bid renders into a dead view.
     */
    fun destroy() {
        nativoBidRequester.cancel()
        nativoBidResponse = null
    }

    /**
     * Picks the higher-priced of the two bids.
     *
     * Prefer this overload. [requestNativoBid] clears [nativoBidResponse] at the start of every request, so
     * resolving against that field couples the outcome to when the other async leg happens to complete.
     * Callers should capture the bid their own cycle received and pass it explicitly.
     */
    fun decideWinner(
        prebidBidResponse: BidResponse?,
        nativoBidResponse: NativoBidResponse?
    ): BidResponse? {
        if (prebidBidResponse == null) {
            return nativoBidResponse
        }
        if (nativoBidResponse == null) {
            return prebidBidResponse
        }
        val prebidPrice = getBidPrice(prebidBidResponse)
        val nativoPrice = getBidPrice(nativoBidResponse)

        if (nativoPrice >= prebidPrice) {
            return nativoBidResponse
        }
        return prebidBidResponse
    }

    /**
     * Resolves against whatever Nativo bid this proxy currently holds.
     *
     * @deprecated Retained for compatibility. Use [decideWinner] with an explicit Nativo bid so the
     * outcome does not depend on when the other async leg happens to complete.
     */
    @Deprecated(
        message = "Pass the cycle's own Nativo bid explicitly.",
        replaceWith = ReplaceWith("decideWinner(prebidBidResponse, nativoBidResponse)")
    )
    fun decideWinner(
        prebidBidResponse: BidResponse?
    ): BidResponse? = decideWinner(prebidBidResponse, nativoBidResponse)

    fun getBidFromResponse(response: BidResponse?): Bid? {
        return response?.getWinningBid()
    }

    fun getBidPrice(response: BidResponse?): Double {
        val bid = getBidFromResponse(response)
        return bid?.price ?: 0.0
    }
}
