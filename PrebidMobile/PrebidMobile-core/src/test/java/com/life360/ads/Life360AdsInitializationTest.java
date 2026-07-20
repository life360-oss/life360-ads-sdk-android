package com.life360.ads;

import static org.junit.Assert.assertFalse;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import com.life360.ads.reflection.Life360AdsReflection;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.reflection.sdk.PrebidMobileReflection;
import org.prebid.mobile.rendering.listeners.SdkInitializationListener;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

/**
 * Tests for the without-Prebid init path. Isolating these here keeps the {@code isPrebidServerEnabled}
 * flag-flip contained: this is the only test class that disables the Prebid Server, so it is the only
 * place that restores the default. Every other test can safely assume the flag is enabled.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LEGACY)
public class Life360AdsInitializationTest {

    @After
    public void tearDown() {
        // Restore the shared singleton state this class mutates so it doesn't leak into other classes.
        PrebidMobileReflection.setFlagsThatSdkIsNotInitialized();
        Life360AdsReflection.setPrebidServerEnabled(true);
    }

    @Test
    public void initializeWithoutPrebid_disablesPrebidServer() {
        // Starts enabled by default; a null context makes init bail immediately (no lingering
        // background threads) while still exercising the flag flip.
        Life360Ads.initializeWithoutPrebid(null, (SdkInitializationListener) null);

        assertFalse(Life360Ads.isPrebidServerEnabled());
    }
}
