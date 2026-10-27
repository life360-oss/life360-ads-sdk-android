package org.prebid.mobile.rendering.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.test.utils.WhiteBox;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class JSLibraryManagerTest {

    /**
     * The singleton caches whichever Context first reached getInstance(), and Robolectric reuses statics
     * across test classes — so a class that built it from a mock Context would otherwise decide what these
     * assertions see.
     */
    @Before
    public void setUp() throws IllegalAccessException {
        WhiteBox.setStaticVariableTo(JSLibraryManager.class, "sInstance", null);
    }

    @After
    public void cleanUp() throws IllegalAccessException {
        WhiteBox.setStaticVariableTo(JSLibraryManager.class, "sInstance", null);
    }

    /**
     * The whole point of bundling omsdk.js is that it is readable on the very first call, with no
     * download and no background read — a regression here silently disables measurement on cold start.
     */
    @Test
    public void getOMSDKScript_readsBundledAssetImmediately() {
        Context context = RuntimeEnvironment.getApplication();

        String script = JSLibraryManager.getInstance(context).getOMSDKScript();

        assertFalse("omsdk.js asset should be readable without any download", script.isEmpty());
        assertTrue("Expected the OMID JS service script", script.contains("omidGlobal"));
    }

    /**
     * Measurement vendors validate the OMID JS service version against the native SDK's, so the
     * bundled script must stay in lockstep with the omsdk-android AAR that BuildConfig records.
     */
    @Test
    public void getOMSDKScript_versionMatchesOmSdkAar() {
        Context context = RuntimeEnvironment.getApplication();

        String script = JSLibraryManager.getInstance(context).getOMSDKScript();

        assertTrue(
                "Bundled omsdk.js must carry the " + com.life360.ads.core.BuildConfig.OMSDK_VERSION + " version string",
                script.contains(com.life360.ads.core.BuildConfig.OMSDK_VERSION)
        );
    }

    @Test
    public void getOMSDKScript_isCachedAcrossCalls() {
        Context context = RuntimeEnvironment.getApplication();
        JSLibraryManager manager = JSLibraryManager.getInstance(context);

        assertEquals(manager.getOMSDKScript(), manager.getOMSDKScript());
    }

    /**
     * The bundled mraid.js must be readable on first call — an empty script on cold start means the first
     * ads render without the MRAID bridge.
     */
    @Test
    public void getMRAIDScript_readsBundledAssetImmediately() {
        Context context = RuntimeEnvironment.getApplication();

        String script = JSLibraryManager.getInstance(context).getMRAIDScript();

        assertFalse("mraid.js asset should be readable without any download", script.isEmpty());
        assertTrue(script.contains("mraid.lastExposure"));
    }

    /**
     * addEventListener('exposureChange') replays this value before native reports, so a 100% placeholder
     * starts the viewability timer for ads that are still off-screen.
     */
    @Test
    public void getMRAIDScript_unmeasuredExposureIsZero() {
        Context context = RuntimeEnvironment.getApplication();

        String script = JSLibraryManager.getInstance(context).getMRAIDScript();

        assertTrue(script.replaceAll("\\s+", "").contains("mraid.lastExposure={exposedPercentage:0.0,"));
    }

}
