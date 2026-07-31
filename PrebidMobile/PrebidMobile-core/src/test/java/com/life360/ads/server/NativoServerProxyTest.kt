package com.life360.ads.server

import com.life360.ads.bid.NativoBidResponse
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
 * (Nativo wins on equal price) is pinned down here.
 */
class NativoServerProxyTest {

    private lateinit var proxy: NativoServerProxy

    @Before
    fun setUp() {
        proxy = NativoServerProxy()
    }

    private fun setNativoResponse(response: NativoBidResponse?) {
        WhiteBox.setInternalState(proxy, "nativoBidResponse", response)
    }

    private fun responseWithPrice(price: Double): BidResponse {
        val bid = Mockito.mock(Bid::class.java)
        Mockito.`when`(bid.price).thenReturn(price)
        val response = Mockito.mock(BidResponse::class.java)
        Mockito.`when`(response.winningBid).thenReturn(bid)
        return response
    }

    private fun nativoResponseWithPrice(price: Double): NativoBidResponse {
        val bid = Mockito.mock(Bid::class.java)
        Mockito.`when`(bid.price).thenReturn(price)
        val response = Mockito.mock(NativoBidResponse::class.java)
        Mockito.`when`(response.winningBid).thenReturn(bid)
        return response
    }

    // region decideWinner

    @Test
    fun decideWinner_bothNull_returnsNull() {
        assertNull(proxy.decideWinner(null))
    }

    @Test
    fun decideWinner_nullPrebid_returnsNativo() {
        val nativo = Mockito.mock(NativoBidResponse::class.java)
        setNativoResponse(nativo)

        assertSame(nativo, proxy.decideWinner(null))
    }

    @Test
    fun decideWinner_nullNativo_returnsPrebid() {
        val prebid = Mockito.mock(BidResponse::class.java)

        assertSame(prebid, proxy.decideWinner(prebid))
    }

    @Test
    fun decideWinner_nativoHigherPrice_returnsNativo() {
        val nativo = nativoResponseWithPrice(5.0)
        setNativoResponse(nativo)

        assertSame(nativo, proxy.decideWinner(responseWithPrice(2.0)))
    }

    @Test
    fun decideWinner_prebidHigherPrice_returnsPrebid() {
        setNativoResponse(nativoResponseWithPrice(1.0))
        val prebid = responseWithPrice(3.0)

        assertSame(prebid, proxy.decideWinner(prebid))
    }

    @Test
    fun decideWinner_equalPrice_favorsNativo() {
        val nativo = nativoResponseWithPrice(2.0)
        setNativoResponse(nativo)

        // `nativoPrice >= prebidPrice` -> a tie goes to Nativo.
        assertSame(nativo, proxy.decideWinner(responseWithPrice(2.0)))
    }

    // endregion

    // region decideWinner must not depend on retained proxy state

    /**
     * `requestNativoBid` clears `nativoBidResponse` at the start of every request, so a caller that resolves
     * against the field loses its own bid as soon as a later request begins. Capturing the bid and passing it
     * explicitly is what makes the outcome independent of request timing.
     */
    @Test
    fun decideWinner_explicitNativoBid_survivesTheFieldBeingReset() {
        val nativo = nativoResponseWithPrice(5.0)
        val prebid = responseWithPrice(2.0)
        setNativoResponse(nativo)

        // A newer request clears the retained bid mid-cycle.
        setNativoResponse(null)

        // The single-argument form loses the Nativo bid...
        assertSame(prebid, proxy.decideWinner(prebid))
        // ...the explicit form still awards it correctly.
        assertSame(nativo, proxy.decideWinner(prebid, nativo))
    }

    @Test
    fun decideWinner_explicitForm_matchesTheRetainedFormWhenNothingHasReset() {
        val nativo = nativoResponseWithPrice(5.0)
        val prebid = responseWithPrice(2.0)
        setNativoResponse(nativo)

        assertSame(proxy.decideWinner(prebid), proxy.decideWinner(prebid, nativo))
    }

    @Test
    fun destroy_cancelsTheRequesterAndClearsTheRetainedBid() {
        val requester = Mockito.mock(com.life360.ads.networking.NativoBidRequester::class.java)
        val proxyWithMock = NativoServerProxy(requester)
        WhiteBox.setInternalState(proxyWithMock, "nativoBidResponse", nativoResponseWithPrice(1.0))

        proxyWithMock.destroy()

        Mockito.verify(requester).cancel()
        assertNull(proxyWithMock.nativoBidResponse)
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
