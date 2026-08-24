package com.life360.ads.bid

import com.life360.ads.renderer.Life360Renderer
import org.json.JSONObject
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.data.bid.Seatbid
import org.prebid.mobile.configuration.AdUnitConfiguration
import java.util.Locale

class Life360BidResponse(
    json: String,
    adUnitConfiguration: AdUnitConfiguration
) : BidResponse(json, adUnitConfiguration) {

    private var life360WinningBid: Bid? = null
    private var winningBidJson : JSONObject? = null
    private var winningSeatbidIndex = -1
    private var winningBidIndex = -1


    init {
        life360WinningBid = selectWinningBid()
        life360WinningBid?.let { bid ->
            applyTargeting(bid)
            applyRendererMeta(bid)
            applyWinningBidTargetingIntoResponseJson(bid)
        }
    }

    override fun getWinningBid(): Bid? {
        return life360WinningBid ?: super.getWinningBid()
    }

    override fun getWinningBidJson(): String? = winningBidJson?.toString() ?: super.getWinningBidJson()

    /**
     * Apply targetting values set in [applyTargeting] to the [Bid] object
     */
    private fun applyWinningBidTargetingIntoResponseJson(bid: Bid) {
        if (winningSeatbidIndex < 0 || winningBidIndex < 0) {
            return
        }
        winningBidJson = responseJson.optJSONArray("seatbid")
            ?.optJSONObject(winningSeatbidIndex)
            ?.optJSONArray("bid")
            ?.optJSONObject(winningBidIndex)
        winningBidJson?.let { json ->
            val ext = json.optJSONObject("ext") ?: JSONObject().also { json.put("ext", it) }
            val prebid = ext.optJSONObject("prebid") ?: JSONObject().also { ext.put("prebid", it) }
            val targeting = prebid.optJSONObject("targeting") ?: JSONObject().also { prebid.put("targeting", it) }
            bid.prebid.targeting.forEach { (key, value) -> targeting.put(key, value) }
        }
    }

    private fun selectWinningBid(): Bid? {
        var winningBid: Bid? = null
        var winningPrice = Double.NEGATIVE_INFINITY
        for ((seatbidIndex, seatbid: Seatbid) in seatbids.withIndex()) {
            for ((bidIndex, bid) in seatbid.bids.withIndex()) {
                if (bid.price > winningPrice) {
                    winningBid = bid
                    winningPrice = bid.price
                    winningSeatbidIndex = seatbidIndex
                    winningBidIndex = bidIndex
                }
            }
        }
        return winningBid
    }

    private fun applyTargeting(bid: Bid) {
        val width = bid.width
        val height = bid.height
        val size = "${width}x${height}"
        val price = String.format(Locale.US, "%.2f", bid.price)

        val targeting = bid.prebid.targeting
        targeting["hb_env"] = "mobile-app"
        targeting["hb_env_nativo"] = "mobile-app"
        targeting["hb_size"] = size
        targeting["hb_size_nativo"] = size
        targeting["hb_bidder"] = "nativo"
        targeting["hb_bidder_nativo"] = "nativo"
        targeting["hb_pb"] = price
        targeting["hb_pb_nativo"] = price
    }

    private fun applyRendererMeta(bid: Bid) {
        val meta = bid.prebid.meta
        meta[KEY_RENDERER_NAME] = Life360Renderer.NAME
        meta[KEY_RENDERER_VERSION] = Life360Renderer.VERSION
    }
}
