package com.life360.demo

import com.life360.ads.rendering.bidding.data.bid.BidResponse
import org.json.JSONObject

/**
 * Reads the diagnostics we want on screen out of a winning bid.
 *
 * The ad type is parsed here rather than through the SDK's own `NativoBidExt.getNativoAdType`
 * because that helper's Kotlin `@Metadata` still names the pre-relocation `org.prebid.mobile.*`
 * parameter types, so a Kotlin caller can't bind to it — see the shaded-metadata note in this
 * project's memory. Java callers are unaffected; a demo doesn't warrant a Java shim.
 */
object NativoBidInspector {

    /** `nativoAdType` values from the exchange's bid ext, mirroring the SDK's `NativoAdType`. */
    private val AD_TYPE_NAMES = mapOf(
        0 to "ARTICLE",
        2 to "DISPLAY",
        3 to "CTP_VIDEO",
        4 to "CAROUSEL",
        5 to "STP_VIDEO",
        6 to "STANDARD_DISPLAY",
        7 to "STORY",
    )

    /**
     * Names the demand source the way the auction resolved it. A Prebid win carries the bidder in
     * the standard `hb_bidder` targeting key; a Nativo win carries `ext.nativo`; anything that
     * loaded without an inspectable bid came from the ad server.
     */
    fun describeDemandSource(response: BidResponse?): String {
        val bid = response?.winningBid ?: return "Ad server (GAM)"
        nativoAdType(bid.jsonString)?.let { return "Nativo ($it)" }
        bid.prebid?.targeting?.get("hb_bidder")?.let { return "Prebid (bidder: $it)" }
        return "Ad server (GAM)"
    }

    /** Rendered creative size as reported by the bid, or null when no bid is inspectable. */
    fun describeCreativeSize(response: BidResponse?): String? {
        val bid = response?.winningBid ?: return null
        if (bid.width <= 0 || bid.height <= 0) return null
        return "${bid.width}×${bid.height}"
    }

    private fun nativoAdType(bidJson: String?): String? {
        val nativo = runCatching {
            JSONObject(bidJson ?: return null)
                .optJSONObject("ext")
                ?.optJSONObject("nativo")
        }.getOrNull() ?: return null

        val rawType = nativo.opt("nativoAdType") as? Number ?: return null
        return AD_TYPE_NAMES[rawType.toInt()] ?: "type ${rawType.toInt()}"
    }
}
