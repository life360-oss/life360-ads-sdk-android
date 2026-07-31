package org.prebid.mobile.reflection.sdk;

import android.content.Context;

import org.mockito.Mockito;
import org.prebid.mobile.Host;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.reflection.Reflection;
import org.prebid.mobile.rendering.sdk.InitializationNotifier;
import org.prebid.mobile.rendering.sdk.PrebidContextHolder;
import org.prebid.mobile.rendering.sdk.SdkInitializer;

public class PrebidMobileReflection {

    public static void setHost(String host) {
        Reflection.setStaticVariableTo(PrebidMobile.class, "host", Host.createCustomHost(host));
    }

    public static void setCustomStatusEndpoint(String url) {
        Reflection.setStaticVariableTo(PrebidMobile.class, "customStatusEndpoint", url);
    }

    public static String getCustomStatusEndpoint() {
        return Reflection.getStaticFieldOf(PrebidMobile.class, "customStatusEndpoint");
    }

    public static void setFlagsThatSdkIsInitialized() {
        Reflection.setStaticVariableTo(InitializationNotifier.class, "tasksCompletedSuccessfully", true);
        Reflection.setStaticVariableTo(InitializationNotifier.class, "initializationInProgress", false);
        PrebidContextHolder.setContext(Mockito.mock(Context.class));
    }

    public static void setFlagsThatSdkIsNotInitialized() {
        Reflection.setStaticVariableTo(InitializationNotifier.class, "tasksCompletedSuccessfully", false);
        Reflection.setStaticVariableTo(InitializationNotifier.class, "initializationInProgress", false);
        // An SDK that has never initialized has never checked a server's status either. Leaving this set
        // would make a later init in the same JVM believe the server was already reported on.
        Reflection.setStaticVariableTo(SdkInitializer.class, "statusCheckedHostUrl", null);
        PrebidContextHolder.clearContext();
    }

    public static void setDisableStatusCheckToTrue() {
        Reflection.setStaticVariableTo(PrebidMobile.class, "disableStatusCheck", true);
    }

    public static void setDisableStatusCheck(boolean disable) {
        Reflection.setStaticVariableTo(PrebidMobile.class, "disableStatusCheck", disable);
    }
}
