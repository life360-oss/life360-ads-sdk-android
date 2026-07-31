package com.life360.ads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;


import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.reflection.sdk.PrebidMobileReflection;
import org.prebid.mobile.rendering.listeners.SdkInitializationListener;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

/**
 * Tests for the Prebid Server mode: the without-Prebid init path, adding a Prebid Server afterwards, and
 * what an ad unit captures at each point.
 * <p>
 * The mode is process-wide singleton state, so every test here restores the default in tearDown.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LEGACY)
public class Life360AdsInitializationTest {

    private static final String SERVER_URL = "https://prebid.example.com/openrtb2/auction";

    @After
    public void tearDown() {
        // Restore the shared singleton state this class mutates so it doesn't leak into other classes.
        PrebidMobileReflection.setFlagsThatSdkIsNotInitialized();
        PrebidMobileReflection.setHost("");
        Life360Ads.setPrebidServerEnabled(true);
    }

    @Test
    public void initializeWithoutPrebid_disablesPrebidServer() {
        // Starts enabled by default; a null context makes init bail immediately (no lingering
        // background threads) while still exercising the flag flip.
        Life360Ads.initializeWithoutPrebid(null, (SdkInitializationListener) null);

        assertFalse(Life360Ads.isPrebidServerEnabled());
    }

    @Test
    public void initializeSdkAfterInitializeWithoutPrebid_enablesPrebidServerAndSetsHost() {
        Life360Ads.initializeWithoutPrebid(null, (SdkInitializationListener) null);

        PrebidMobile.initializeSdk(null, SERVER_URL, null);

        assertTrue(Life360Ads.isPrebidServerEnabled());
        assertTrue(PrebidMobile.getPrebidServerHost().getHostUrl().equals(SERVER_URL));
    }

    @Test
    public void adUnitCreatedWhileServerless_staysServerlessAfterPrebidServerIsAdded() {
        Life360Ads.initializeWithoutPrebid(null, (SdkInitializationListener) null);
        AdUnitConfiguration serverlessAdUnit = new AdUnitConfiguration();

        PrebidMobile.initializeSdk(null, SERVER_URL, null);

        // The whole point of capturing at creation: an ad unit that may already be loading and refreshing
        // must not change legs part-way through when a server appears.
        assertFalse(serverlessAdUnit.isPrebidServerEnabled());
        assertTrue(new AdUnitConfiguration().isPrebidServerEnabled());
    }

    @Test
    public void adUnitCreatedWithPrebidServer_keepsPrebidAfterServerlessInit() {
        AdUnitConfiguration prebidAdUnit = new AdUnitConfiguration();

        Life360Ads.initializeWithoutPrebid(null, (SdkInitializationListener) null);

        assertTrue(prebidAdUnit.isPrebidServerEnabled());
        assertFalse(new AdUnitConfiguration().isPrebidServerEnabled());
    }
}
