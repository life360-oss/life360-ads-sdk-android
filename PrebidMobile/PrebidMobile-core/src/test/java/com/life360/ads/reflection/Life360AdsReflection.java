package com.life360.ads.reflection;

import com.life360.ads.Life360Ads;

import org.prebid.mobile.reflection.Reflection;

/**
 * Test-only reflection access to {@link Life360Ads} internal state, mirroring the pattern used by
 * {@code PrebidMobileReflection}. Keeping the reflected field name in this single place means a
 * rename only has to be fixed here rather than at every test that manipulates the flag.
 */
public class Life360AdsReflection {

    public static void setPrebidServerEnabled(boolean enabled) {
        // isPrebidServerEnabled is private-set on the Life360Ads object; its backing field lives on
        // the singleton instance.
        Reflection.setVariableTo(Life360Ads.INSTANCE, "isPrebidServerEnabled", enabled);
    }
}
