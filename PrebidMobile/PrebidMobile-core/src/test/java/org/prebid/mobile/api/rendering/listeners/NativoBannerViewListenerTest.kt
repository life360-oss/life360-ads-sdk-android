package org.prebid.mobile.api.rendering.listeners

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.BannerView

/**
 * Guards the opt-in contract that replaced the old reflection-based detection: BannerView routes
 * to the Nativo render path only when the listener is a [NativoBannerViewListener]. This is an
 * `instanceof` check rather than reflection precisely so it survives R8 minification without a
 * consumer keep rule — see [NativoBannerViewListener]. If someone folds `onNativoAdLoaded` back
 * onto the base interface, these assertions break and flag the regression.
 */
class NativoBannerViewListenerTest {

    /** Implements only the base listener — has not opted into the Nativo callback. */
    private open class BaseListener : BannerViewListener {
        override fun onAdLoaded(bannerView: BannerView?) {}
        override fun onAdDisplayed(bannerView: BannerView?) {}
        override fun onAdFailed(bannerView: BannerView?, exception: AdException?) {}
        override fun onAdClicked(bannerView: BannerView?) {}
        override fun onAdClosed(bannerView: BannerView?) {}
    }

    /** Opts into the Nativo render path by implementing the extension interface. */
    private class NativoAwareListener : BaseListener(), NativoBannerViewListener {
        override fun onNativoAdLoaded(bannerView: BannerView?) {}
    }

    @Test
    fun baseListener_isNotNativoAware() {
        assertFalse(BaseListener() is NativoBannerViewListener)
    }

    @Test
    fun nativoAwareListener_isNativoAware() {
        assertTrue(NativoAwareListener() is NativoBannerViewListener)
    }

    @Test
    fun nativoAwareListener_isAlsoBaseListener() {
        assertTrue(NativoAwareListener() is BannerViewListener)
    }
}
