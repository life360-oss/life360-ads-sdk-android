package com.life360.ads.bid

import com.life360.ads.renderer.Life360Renderer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse

/**
 * Regression tests for [Life360BidResponse] `applyTargeting`: price strings (`hb_pb` / `hb_pb_nativo`)
 * and creative size (`hb_size` / `hb_size_nativo`) derived from the winning bid.
 */
class Life360BidResponseTest {

    @Test
    fun hb_pb_formatsPriceWithTwoDecimalPlaces() {
        val price = 29.80
        val response = life360BidResponse(singleBidJson(price))

        assertFalse(response.hasParseError())
        val bid = response.winningBid
        assertNotNull(bid)
        val targeting = bid!!.prebid.targeting

        assertEquals("29.80", targeting["hb_pb"])
        assertEquals("29.80", targeting["hb_pb_nativo"])
    }

    @Test
    fun hb_pb_forWholeNumberPrice_usesTwoFractionDigits() {
        val response = life360BidResponse(singleBidJson(5.0))

        val bid = response.winningBid!!
        val hbPb = bid.prebid.targeting["hb_pb"]

        assertEquals("5.00", hbPb)
    }

    @Test
    fun hb_pb_forOnePointFive_usesTwoFractionDigits() {
        val response = life360BidResponse(singleBidJson(1.5))

        val bid = response.winningBid!!
        val hbPb = bid.prebid.targeting["hb_pb"]

        assertEquals("1.50", hbPb)
    }

    @Test
    fun hb_pb_usesHighestPricedBid() {
        val low = bidJson("low", price = 0.5)
        val high = bidJson("high", price = 2.75)
        val json = responseJson(JSONArray().put(low).put(high))
        val response = life360BidResponse(json)

        val winning = response.winningBid!!
        assertEquals("high", winning.id)
        assertEquals("2.75", winning.prebid.targeting["hb_pb"])
    }

    @Test
    fun hb_pb_forTypicalCpm_matchesTwoDecimalFormatting() {
        val response = life360BidResponse(singleBidJson(0.15))

        val bid = response.winningBid!!
        assertEquals("0.15", bid.prebid.targeting["hb_pb"])
        assertEquals("0.15", bid.prebid.targeting["hb_pb_nativo"])
    }

    @Test
    fun hb_size_and_hb_size_nativo_areWidthTimesHeightFromWinningBid() {
        val response = life360BidResponse(singleBidJson(price = 1.0, width = 320, height = 50))

        val bid = response.winningBid!!
        val targeting = bid.prebid.targeting
        val expected = "320x50"

        assertEquals(expected, targeting["hb_size"])
        assertEquals(expected, targeting["hb_size_nativo"])
    }

    @Test
    fun hb_size_usesWinningBidDimensionsWhenBidsHaveDifferentSizes() {
        val smallBanner = bidJson("small", price = 1.0, width = 320, height = 50)
        val leaderboard = bidJson("leader", price = 5.0, width = 728, height = 90)
        val response = life360BidResponse(responseJson(JSONArray().put(smallBanner).put(leaderboard)))

        val winning = response.winningBid!!
        assertEquals("leader", winning.id)
        assertEquals("728x90", winning.prebid.targeting["hb_size"])
        assertEquals("728x90", winning.prebid.targeting["hb_size_nativo"])
    }

    @Test
    fun hb_size_whenWidthHeightMissing_defaultsToZeroByOpenRtbParser() {
        val bid = JSONObject()
            .put("id", "no-wh")
            .put("impid", "imp1")
            .put("price", 1.0)
            .put("adm", "adm")
            .put("ext", JSONObject().put("prebid", JSONObject()))
        val json = responseJson(JSONArray().put(bid))
        val response = life360BidResponse(json)

        val winning = response.winningBid!!
        assertEquals(0, winning.width)
        assertEquals(0, winning.height)
        assertEquals("0x0", winning.prebid.targeting["hb_size"])
        assertEquals("0x0", winning.prebid.targeting["hb_size_nativo"])
    }

    @Test
    fun applyRendererMeta_setsLife360RendererNameAndVersionOnWinningBid() {
        val response = life360BidResponse(singleBidJson(1.0))

        val meta = response.winningBid!!.prebid.meta
        assertEquals(Life360Renderer.NAME, meta[BidResponse.KEY_RENDERER_NAME])
        assertEquals(Life360Renderer.VERSION, meta[BidResponse.KEY_RENDERER_VERSION])
    }

    @Test
    fun selectWinningBid_picksHighestAcrossMultipleSeatbids() {
        val seatA = JSONObject().put("seat", "a").put("bid", JSONArray().put(bidJson("a1", 1.0)))
        val seatB = JSONObject().put("seat", "b").put("bid", JSONArray().put(bidJson("b1", 4.0)))
        val json = JSONObject()
            .put("id", "resp1")
            .put("seatbid", JSONArray().put(seatA).put(seatB))
            .toString()

        val response = life360BidResponse(json)

        assertEquals("b1", response.winningBid!!.id)
    }

    @Test
    fun winningBid_whenNoSeatbids_isNullAndNoTargetingApplied() {
        val json = JSONObject().put("id", "resp1").put("seatbid", JSONArray()).toString()

        val response = life360BidResponse(json)

        // No Life360 bid selected, so getWinningBid() falls through to the base response (null here).
        assertNull(response.winningBid)
    }

    @Test
    fun winningBid_whenSeatbidHasNoBids_isNull() {
        val emptySeat = JSONObject().put("seat", "nativo").put("bid", JSONArray())
        val json = JSONObject().put("id", "resp1").put("seatbid", JSONArray().put(emptySeat)).toString()

        val response = life360BidResponse(json)

        assertNull(response.winningBid)
    }

    @Test
    fun responseJson_viaABidResponseTypedReference_stillReturnsThePatchedJson() {
        // Declared as the base type on purpose: proves virtual dispatch, not the static type, decides
        // which override runs — a consumer holding a plain BidResponse reference still gets the patch.
        val response: BidResponse = life360BidResponse(singleBidJson(price = 2.75, width = 320, height = 50))

        val targeting = winningBidTargetingIn(response.responseJson)

        assertEquals("nativo", targeting?.optString("hb_bidder"))
    }

    @Test
    fun responseJson_returnsTheSameObjectEveryCall_ratherThanACopy() {
        // The fix is applied by mutating this one object in init, not by returning a patched copy —
        // so any two calls, from any reference type, must resolve to the exact same instance.
        val response = life360BidResponse(singleBidJson(price = 1.0))

        assertSame(response.responseJson, response.responseJson)
    }

    @Test
    fun responseJson_includesTargetingAppliedToTheWinningBid() {
        val response = life360BidResponse(singleBidJson(price = 2.75, width = 320, height = 50))

        val targeting = winningBidTargetingIn(response.responseJson)

        assertEquals("nativo", targeting?.optString("hb_bidder"))
        assertEquals("2.75", targeting?.optString("hb_pb"))
        assertEquals("320x50", targeting?.optString("hb_size"))
        assertEquals("mobile-app", targeting?.optString("hb_env"))
    }

    @Test
    fun responseJson_preservesTargetingKeysTheServerAlreadySet() {
        val bid = bidJson("bid1", price = 1.0).apply {
            getJSONObject("ext").getJSONObject("prebid").put(
                "targeting",
                JSONObject().put("hb_cache_id", "cache-123")
            )
        }
        val response = life360BidResponse(responseJson(JSONArray().put(bid)))

        val targeting = winningBidTargetingIn(response.responseJson)

        assertEquals("cache-123", targeting?.optString("hb_cache_id"))
        assertEquals("nativo", targeting?.optString("hb_bidder"))
    }

    @Test
    fun responseJson_patchesTheWinningBidWhenItIsNotTheFirstSeatbidOrBid() {
        val seatA = JSONObject().put("seat", "a").put("bid", JSONArray().put(bidJson("a1", 1.0)))
        val seatB = JSONObject().put("seat", "b").put(
            "bid",
            JSONArray().put(bidJson("b1", 2.0)).put(bidJson("b2", 4.0))
        )
        val json = JSONObject()
            .put("id", "resp1")
            .put("seatbid", JSONArray().put(seatA).put(seatB))
            .toString()
        val response = life360BidResponse(json)

        val patchedSeatB = response.responseJson.getJSONArray("seatbid").getJSONObject(1)
        val patchedA1Targeting = patchedSeatB.getJSONArray("bid").getJSONObject(0)
            .getJSONObject("ext").getJSONObject("prebid").optJSONObject("targeting")
        val patchedB2Targeting = patchedSeatB.getJSONArray("bid").getJSONObject(1)
            .getJSONObject("ext").getJSONObject("prebid").getJSONObject("targeting")

        // Only the actual winning bid (b2, price 4.0) gets patched, not another bid at the same index.
        assertNull(patchedA1Targeting?.opt("hb_bidder"))
        assertEquals("nativo", patchedB2Targeting.optString("hb_bidder"))
    }

    @Test
    fun responseJson_whenNoWinningBid_returnsTheUnmodifiedServerResponse() {
        val json = JSONObject().put("id", "resp1").put("seatbid", JSONArray()).toString()

        val response = life360BidResponse(json)

        assertEquals("resp1", response.responseJson.optString("id"))
        assertEquals(0, response.responseJson.getJSONArray("seatbid").length())
    }

    @Test
    fun winningBidJson_includesTargetingAppliedToTheWinningBid() {
        val response = life360BidResponse(singleBidJson(price = 2.75, width = 320, height = 50))

        val targeting = JSONObject(response.winningBidJson!!)
            .getJSONObject("ext").getJSONObject("prebid").getJSONObject("targeting")

        assertEquals("nativo", targeting.optString("hb_bidder"))
        assertEquals("2.75", targeting.optString("hb_pb"))
        assertEquals("320x50", targeting.optString("hb_size"))
    }

    @Test
    fun winningBidJson_isScopedToJustTheWinningBid_notTheFullResponse() {
        val low = bidJson("low", price = 0.5)
        val high = bidJson("high", price = 2.75)
        val response = life360BidResponse(responseJson(JSONArray().put(low).put(high)))

        val winningBidJson = JSONObject(response.winningBidJson!!)

        assertEquals("high", winningBidJson.optString("id"))
        assertFalse(winningBidJson.has("seatbid"))
    }

    @Test
    fun winningBidJson_whenNoWinningBid_doesNotThrow() {
        val json = JSONObject().put("id", "resp1").put("seatbid", JSONArray()).toString()

        val response = life360BidResponse(json)

        assertEquals(json, response.winningBidJson)
    }

    private fun winningBidTargetingIn(responseJson: JSONObject): JSONObject? {
        val seatbid = responseJson.getJSONArray("seatbid").getJSONObject(0)
        val bid = seatbid.getJSONArray("bid").getJSONObject(0)
        return bid.getJSONObject("ext").getJSONObject("prebid").optJSONObject("targeting")
    }

    private fun life360BidResponse(json: String): Life360BidResponse {
        return Life360BidResponse(json, AdUnitConfiguration())
    }

    private fun singleBidJson(price: Double, width: Int = 320, height: Int = 50): String {
        return responseJson(JSONArray().put(bidJson("bid1", price, width, height)))
    }

    private fun responseJson(bids: JSONArray): String {
        val seatbid = JSONObject()
            .put("bid", bids)
            .put("seat", "nativo")
        return JSONObject()
            .put("id", "resp1")
            .put("seatbid", JSONArray().put(seatbid))
            .toString()
    }

    private fun bidJson(
        id: String,
        price: Double,
        width: Int = 320,
        height: Int = 50,
    ): JSONObject {
        val prebid = JSONObject()
        val ext = JSONObject().put("prebid", prebid)
        return JSONObject()
            .put("id", id)
            .put("impid", "imp1")
            .put("price", price)
            .put("adm", "adm")
            .put("w", width)
            .put("h", height)
            .put("ext", ext)
    }
}
