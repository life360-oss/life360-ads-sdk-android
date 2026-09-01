/*
 *    Copyright 2018-2021 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile.rendering.sdk;

import android.content.Context;

import org.prebid.mobile.LogUtil;
import org.prebid.mobile.rendering.sdk.scripts.JsScriptData;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetcher for the JS scripts needed by the SDK (omsdk.js, mraid.js).
 * Top level class for working with JS scripts.
 * <p>
 * The two scripts load by different mechanisms. omsdk.js is a packaged asset, read synchronously on
 * first use, because measurement vendors validate the OMID service version against the native SDK's
 * and a runtime-fetched script cannot guarantee that pairing. mraid.js is still downloaded in the
 * background, so {@link #getMRAIDScript()} returns an empty string until that completes.
 */
public class JSLibraryManager {

    private static final String TAG = "JSLibraryManager";

    /**
     * Bundled alongside the omsdk-android AAR, and kept in lockstep with it — a mismatch between the
     * OMID JS service and the native SDK can invalidate measurement, so both move in the same commit.
     */
    private static final String OMSDK_ASSET_PATH = "omsdk.js";

    private static JSLibraryManager sInstance;

    private final Context context;

    private String MRAIDscript = "";
    private String OMSDKscirpt = "";
    private JsScriptsDownloader scriptsDownloader;

    private JSLibraryManager(Context context) {
        // Callers reach getInstance() with Activity contexts too, and this singleton outlives them.
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        this.scriptsDownloader = JsScriptsDownloader.createDownloader(context);
    }

    public static JSLibraryManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (JSLibraryManager.class) {
                if (sInstance == null) {
                    sInstance = new JSLibraryManager(context);
                }
            }
        }
        return sInstance;
    }

    public boolean checkIfScriptsDownloadedAndStartDownloadingIfNot() {
        if (scriptsDownloader.areScriptsDownloadedAlready()) {
            if (!MRAIDscript.isEmpty()) {
                return true;
            }

            startScriptReadingTask();
            return false;
        }

        scriptsDownloader.downloadScripts(
                (path) -> new JsScriptsDownloader.ScriptDownloadListener(path, scriptsDownloader.storage)
        );
        return false;
    }

    public void startScriptReadingTask() {
        if (scriptsDownloader.areScriptsDownloadedAlready()) {
            if (MRAIDscript.isEmpty()) {

                boolean isNotRunning = BackgroundScriptReader.alreadyRunning.compareAndSet(false, true);
                if (isNotRunning) {
                    Thread thread = new Thread(new BackgroundScriptReader(scriptsDownloader, this));
                    thread.start();
                }
            }
        }
    }

    public String getMRAIDScript() {
        return MRAIDscript;
    }

    /**
     * Reads from assets on first call rather than at construction so that a corrupt or missing asset
     * surfaces as one logged error and a disabled-OM path, not as a failure to initialize the SDK.
     *
     * @return the OMID JS service script, or an empty string if the asset could not be read.
     */
    public synchronized String getOMSDKScript() {
        if (OMSDKscirpt.isEmpty()) {
            OMSDKscirpt = readAsset(OMSDK_ASSET_PATH);
        }
        return OMSDKscirpt;
    }

    private String readAsset(String path) {
        try (InputStream is = context.getAssets().open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Throwable throwable) {
            LogUtil.error(TAG, "Can't read asset: " + path, throwable);
            return "";
        }
    }

    private static class BackgroundScriptReader implements Runnable {

        private static final AtomicBoolean alreadyRunning = new AtomicBoolean(false);

        private final JsScriptsDownloader scriptsDownloader;
        private final JSLibraryManager jsLibraryManager;

        public BackgroundScriptReader(
                JsScriptsDownloader scriptsDownloader,
                JSLibraryManager jsLibraryManager
        ) {
            this.scriptsDownloader = scriptsDownloader;
            this.jsLibraryManager = jsLibraryManager;
        }

        @Override
        public void run() {
            String mraidScript = scriptsDownloader.readFile(JsScriptData.mraidData);

            jsLibraryManager.MRAIDscript = mraidScript;
            alreadyRunning.set(false);
        }

    }

}
