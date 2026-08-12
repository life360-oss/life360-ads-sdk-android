package org.prebid.mobile.api.rendering.listeners

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.BannerView

/**
 * Guards the opt-in contract that replaced the old reflection-based detection: BannerView routes
 * to the Life360 render path only when the listener is a [Life360BannerViewListener]. This is an
 * `instanceof` check rather than reflection precisely so it survives R8 minification without a
 * consumer keep rule — see [Life360BannerViewListener]. If someone folds `onLife360AdLoaded` back
 * onto the base interface, these assertions break and flag the regression.
 */
class Life360BannerViewListenerTest {

    /** Implements only the base listener — has not opted into the Life360 callback. */
    private open class BaseListener : BannerViewListener {
        override fun onAdLoaded(bannerView: BannerView?) {}
        override fun onAdDisplayed(bannerView: BannerView?) {}
        override fun onAdFailed(bannerView: BannerView?, exception: AdException?) {}
        override fun onAdClicked(bannerView: BannerView?) {}
        override fun onAdClosed(bannerView: BannerView?) {}
    }

    /** Opts into the Life360 render path by implementing the extension interface. */
    private class Life360AwareListener : BaseListener(), Life360BannerViewListener {
        override fun onLife360AdLoaded(bannerView: BannerView?) {}
    }

    @Test
    fun baseListener_isNotLife360Aware() {
        assertFalse(BaseListener() is Life360BannerViewListener)
    }

    @Test
    fun life360AwareListener_isLife360Aware() {
        assertTrue(Life360AwareListener() is Life360BannerViewListener)
    }

    @Test
    fun life360AwareListener_isAlsoBaseListener() {
        assertTrue(Life360AwareListener() is BannerViewListener)
    }
}
