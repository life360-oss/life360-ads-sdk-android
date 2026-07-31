package org.prebid.mobile.rendering.sdk;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.life360.ads.Life360Ads;
import com.life360.ads.renderer.NativoRenderer;
import org.prebid.mobile.LogUtil;
import org.prebid.mobile.LogUtil.PrebidLogger;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.rendering.PrebidRenderer;
import org.prebid.mobile.rendering.listeners.SdkInitializationListener;
import org.prebid.mobile.rendering.session.manager.OmAdSessionManager;
import org.prebid.mobile.rendering.utils.helpers.AdvertisingIdManager;
import org.prebid.mobile.rendering.utils.helpers.AppInfoManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SdkInitializer {

    private static final String TAG = SdkInitializer.class.getSimpleName();

    @Nullable
    private static volatile String statusCheckedHostUrl = null;

    public static void init(
            @Nullable Context context,
            @Nullable SdkInitializationListener listener
    ) {
        if (InitializationNotifier.isInitializationInProgress()) {
            if (listener != null) {
                LogUtil.error(TAG, "Initialization is already in progress. This listener will not be called; "
                        + "wait for the one passed to the pending call.");
            }
            return;
        }
        if (PrebidMobile.isSdkInitialized()) {
            applyConfigurationChange(listener);
            return;
        }

        InitializationNotifier initializationNotifier = new InitializationNotifier(listener);

        Context applicationContext = getApplicationContext(context);
        if (applicationContext == null) {
            initializationNotifier.initializationFailed("Context must be not null!");
            return;
        }

        LogUtil.debug(TAG, "Initializing Prebid SDK");
        PrebidContextHolder.setContext(applicationContext);

        if (PrebidMobile.getLogLevel() != null) {
            LogUtil.setLogLevel(PrebidMobile.getLogLevel().getValue());
        }

        PrebidLogger customLogger = PrebidMobile.getCustomLogger();
        if (customLogger != null) {
            LogUtil.setLogger(customLogger);
        }

        try {
            PrebidMobile.registerPluginRenderer(new PrebidRenderer());

            // Register Nativo rendering plugin as a default option
            PrebidMobile.registerPluginRenderer(new NativoRenderer());

            AppInfoManager.init(applicationContext);

            OmAdSessionManager.activateOmSdk(applicationContext);

            ManagersResolver.getInstance().prepare(applicationContext);

            JSLibraryManager.getInstance(applicationContext).checkIfScriptsDownloadedAndStartDownloadingIfNot();
        } catch (Throwable throwable) {
            initializationNotifier.initializationFailed("Exception during initialization: " + throwable.getMessage() + "\n" + Log.getStackTraceString(throwable));
            return;
        }

        new Thread(() -> runBackgroundTasks(
                initializationNotifier,
                Executors.newFixedThreadPool(2))
        ).start();
    }

    @VisibleForTesting
    public static void runBackgroundTasks(
            InitializationNotifier initializationNotifier,
            ExecutorService executor
    ) {
        try {
            Future<String> statusRequesterResult = null;
            if (shouldCheckServerStatus()) {
                statusCheckedHostUrl = PrebidMobile.getPrebidServerHost().getHostUrl();
                statusRequesterResult = executor.submit(new StatusRequester());
            } else {
                LogUtil.debug(TAG, "Prebid SDK initialization skipping status check");
            }
            executor.execute(new UserConsentFetcherTask());
            executor.execute(new UserAgentFetcherTask());
            executor.execute(AdvertisingIdManager::initAdvertisingId);
            executor.shutdown();

            boolean terminatedByTimeout = !executor.awaitTermination(10, TimeUnit.SECONDS);
            if (terminatedByTimeout) {
                initializationNotifier.initializationFailed("Terminated by timeout.");
                return;
            }

            String statusRequesterError = statusRequesterResult != null ? statusRequesterResult.get() : null;
            initializationNotifier.initializationCompleted(statusRequesterError);
        } catch (Exception exception) {
            initializationNotifier.initializationFailed("Exception during initialization: " + Log.getStackTraceString(exception));
        }
    }

    /**
     * Handles an init call that arrives once the SDK is already up — most often a Prebid Server being added
     * to an app that initialized serverless.
     * <p>
     * The one-time setup (renderers, OM SDK, context, managers, JS libraries) is already done and must not
     * run twice, so all that remains is the /status check the new server is owed and the caller's listener.
     */
    private static void applyConfigurationChange(@Nullable SdkInitializationListener listener) {
        String hostUrl = PrebidMobile.getPrebidServerHost().getHostUrl();
        boolean statusCheckOwed = shouldCheckServerStatus() && !hostUrl.equals(statusCheckedHostUrl);

        if (!statusCheckOwed) {
            // Ad views re-enter init from their constructors with a null listener, so with nothing to report
            // this has to stay free of side effects.
            if (listener != null) {
                new InitializationNotifier(listener).initializationCompleted(null);
            }
            return;
        }

        statusCheckedHostUrl = hostUrl;
        InitializationNotifier initializationNotifier = new InitializationNotifier(listener);
        new Thread(() -> {
            String statusRequesterError;
            try {
                statusRequesterError = StatusRequester.makeRequest();
            } catch (Throwable throwable) {
                statusRequesterError = "Exception during status check: " + throwable.getMessage();
            }
            // A bad status is a warning, never a failure: the SDK is initialized and every non-Prebid leg
            // still works, so the context must survive.
            initializationNotifier.initializationCompleted(statusRequesterError);
        }).start();
    }

    private static boolean shouldCheckServerStatus() {
        return !PrebidMobile.shouldDisableStatusCheck() && Life360Ads.isPrebidServerEnabled();
    }

    @Nullable
    private static Context getApplicationContext(
            @Nullable Context context
    ) {
        if (context instanceof Application) {
            return context;
        } else if (context != null) {
            return context.getApplicationContext();
        }
        return null;
    }

    protected static class UserConsentFetcherTask implements Runnable {

        @Override
        public void run() {
            ManagersResolver.getInstance().getUserConsentManager().initConsentValues();
        }

    }

}
