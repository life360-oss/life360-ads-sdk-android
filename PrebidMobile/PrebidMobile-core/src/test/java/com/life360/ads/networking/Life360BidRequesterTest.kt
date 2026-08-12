package com.life360.ads.networking

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.prebid.mobile.api.exceptions.NoBidException
import org.prebid.mobile.rendering.networking.BaseNetworkTask
import org.prebid.mobile.rendering.networking.ResponseHandler
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode

/**
 * Pins the contract Life360BidRequester relies on: BaseNetworkTask must surface a 204 as a
 * [NoBidException] (and nothing else as one). Life360BidRequester dispatches no-bids purely by this
 * type, so if a 204 stopped throwing NoBidException the publisher would regress to a generic
 * internal error instead of a clean no-bid result.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.LEGACY)
class Life360BidRequesterTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun realServer204_throwsNoBidException() {
        val exception = exceptionForResponseCode(204)
        assertNotNull("Expected a 204 to surface an exception", exception)
        assertTrue("A 204 must surface as NoBidException, was ${exception?.javaClass}", exception is NoBidException)
    }

    @Test
    fun realServer4xx_isNotNoBidException() {
        val exception = exceptionForResponseCode(400)
        assertNotNull("Expected a 400 to surface an exception", exception)
        assertFalse("Only a 204 should map to NoBidException", exception is NoBidException)
    }

    /** Returns the exception BaseNetworkTask surfaces for the given response code, or null on success. */
    private fun exceptionForResponseCode(code: Int): Exception? {
        server.enqueue(MockResponse().setResponseCode(code))

        var captured: Exception? = null
        val handler = object : ResponseHandler {
            override fun onResponse(response: BaseNetworkTask.GetUrlResult) {}
            override fun onError(msg: String?, responseTime: Long) {}
            override fun onErrorWithException(e: Exception?, responseTime: Long) {
                captured = e
            }
        }

        val params = BaseNetworkTask.GetUrlParams().apply {
            name = "nativo_bid_request"
            userAgent = "user-agent"
            url = server.url("/bid").toString()
            requestType = "GET"
        }

        try {
            BaseNetworkTask(handler).execute(params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return captured
    }
}
