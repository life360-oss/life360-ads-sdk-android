package com.life360.ads.server

import com.life360.ads.bid.Life360BidResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.test.utils.WhiteBox

/**
 * Covers the head-to-head auction logic in [NativoServerProxy.decideWinner] and its price helpers.
 * This decides which demand source renders, so every null-handling branch and the tie-break
 * (Life360 wins on equal price) is pinned down here.
 */
class NativoServerProxyTest {

    private lateinit var proxy: NativoServerProxy

    @Before
    fun setUp() {
        proxy = NativoServerProxy()
    }

    private fun setLife360Response(response: Life360BidResponse?) {
        WhiteBox.setInternalState(proxy, "life360BidResponse", response)
    }

    private fun responseWithPrice(price: Double): BidResponse {
        val bid = Mockito.mock(Bid::class.java)
        Mockito.`when`(bid.price).thenReturn(price)
        val response = Mockito.mock(BidResponse::class.java)
        Mockito.`when`(response.winningBid).thenReturn(bid)
        return response
    }

    private fun life360ResponseWithPrice(price: Double): Life360BidResponse {
        val bid = Mockito.mock(Bid::class.java)
        Mockito.`when`(bid.price).thenReturn(price)
        val response = Mockito.mock(Life360BidResponse::class.java)
        Mockito.`when`(response.winningBid).thenReturn(bid)
        return response
    }

    // region decideWinner

    @Test
    fun decideWinner_bothNull_returnsNull() {
        assertNull(proxy.decideWinner(null))
    }

    @Test
    fun decideWinner_nullPrebid_returnsLife360() {
        val life360 = Mockito.mock(Life360BidResponse::class.java)
        setLife360Response(life360)

        assertSame(life360, proxy.decideWinner(null))
    }

    @Test
    fun decideWinner_nullLife360_returnsPrebid() {
        val prebid = Mockito.mock(BidResponse::class.java)

        assertSame(prebid, proxy.decideWinner(prebid))
    }

    @Test
    fun decideWinner_life360HigherPrice_returnsLife360() {
        val life360 = life360ResponseWithPrice(5.0)
        setLife360Response(life360)

        assertSame(life360, proxy.decideWinner(responseWithPrice(2.0)))
    }

    @Test
    fun decideWinner_prebidHigherPrice_returnsPrebid() {
        setLife360Response(life360ResponseWithPrice(1.0))
        val prebid = responseWithPrice(3.0)

        assertSame(prebid, proxy.decideWinner(prebid))
    }

    @Test
    fun decideWinner_equalPrice_favorsLife360() {
        val life360 = life360ResponseWithPrice(2.0)
        setLife360Response(life360)

        // `life360Price >= prebidPrice` -> a tie goes to Life360.
        assertSame(life360, proxy.decideWinner(responseWithPrice(2.0)))
    }

    // endregion

    // region getBidFromResponse / getBidPrice

    @Test
    fun getBidFromResponse_nullResponse_returnsNull() {
        assertNull(proxy.getBidFromResponse(null))
    }

    @Test
    fun getBidFromResponse_returnsWinningBid() {
        val bid = Mockito.mock(Bid::class.java)
        val response = Mockito.mock(BidResponse::class.java)
        Mockito.`when`(response.winningBid).thenReturn(bid)

        assertSame(bid, proxy.getBidFromResponse(response))
    }

    @Test
    fun getBidPrice_nullResponse_returnsZero() {
        assertEquals(0.0, proxy.getBidPrice(null), 0.0)
    }

    @Test
    fun getBidPrice_noWinningBid_returnsZero() {
        val response = Mockito.mock(BidResponse::class.java)
        Mockito.`when`(response.winningBid).thenReturn(null)

        assertEquals(0.0, proxy.getBidPrice(response), 0.0)
    }

    @Test
    fun getBidPrice_returnsWinningBidPrice() {
        assertEquals(4.25, proxy.getBidPrice(responseWithPrice(4.25)), 0.0)
    }

    // endregion
}
