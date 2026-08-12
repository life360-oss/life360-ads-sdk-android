package com.life360.ads.bid

import org.json.JSONObject
import org.prebid.mobile.rendering.bidding.data.bid.Bid

object Life360BidExt {
    private const val EXT_KEY = "ext"
    private const val NATIVO_KEY = "nativo"
    private const val OWNED_OPERATED_KEY = "oo"
    private const val AD_TYPE_KEY = "nativoAdType"

    @JvmStatic
    fun isOwnedOperated(bid: Bid?): Boolean {
        if (bid == null) {
            return false
        }
        val json = bid.jsonString ?: return false
        return try {
            val root = JSONObject(json)
            val ext = root.optJSONObject(EXT_KEY) ?: return false
            val nativo = ext.optJSONObject(NATIVO_KEY) ?: return false
            when (val raw = nativo.opt(OWNED_OPERATED_KEY)) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
    
    @JvmStatic
    fun getLife360AdType(bid: Bid?): Life360AdType? {
        if (bid == null) {
            return null
        }
        val json = bid.jsonString ?: return null
        return try {
            val root = JSONObject(json)
            val ext = root.optJSONObject(EXT_KEY) ?: return null
            val nativo = ext.optJSONObject(NATIVO_KEY) ?: return null
            when (val raw = nativo.opt(AD_TYPE_KEY)) {
                is Number -> Life360AdType.fromInt(raw.toInt())
                else -> return null
            }
        } catch (_: Exception) {
            null
        }
    }
    
}
