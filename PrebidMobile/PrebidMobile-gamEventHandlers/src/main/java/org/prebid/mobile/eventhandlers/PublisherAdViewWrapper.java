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

package org.prebid.mobile.eventhandlers;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.admanager.AdManagerAdView;
import com.google.android.gms.ads.admanager.AppEventListener;

import android.view.ViewGroup;
import android.webkit.WebView;

import org.prebid.mobile.AdSize;
import org.prebid.mobile.LogUtil;
import org.prebid.mobile.eventhandlers.global.Constants;
import org.prebid.mobile.eventhandlers.utils.GamUtils;
import org.prebid.mobile.rendering.bidding.data.bid.Bid;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal wrapper of PublisherAdView from GAM SDK.
 * To achieve safe integration between various GAM SDK versions we have to wrap all PublisherAdView method execution in try / catch.
 * This class instance should be created via newInstance method, which will catch any potential exception on PublisherAdView / PublisherAdViewWrapper instance creation
 */
public class PublisherAdViewWrapper extends AdListener implements AppEventListener {

    private static final String TAG = PublisherAdViewWrapper.class.getSimpleName();

    /**
     * Element ID in the GAM Prebid creative whose href contains the click tracking URL
     * populated by GAM's {@code %%CLICK_URL_UNESC%%} macro.
     */
    static final String GAM_CLICK_ELEMENT_ID = "exchange-ping-url";

    private static final String JS_EXTRACT_CLICK_URL = "(function() {"
            + "var el = document.getElementById('" + GAM_CLICK_ELEMENT_ID + "');"
            + "return el ? el.href : '';"
            + "})()";

    private final AdManagerAdView adView;
    private final GamAdEventListener listener;

    private PublisherAdViewWrapper(
            Context context,
            String gamAdUnit,
            GamAdEventListener eventListener,
            AdSize... adSizes
    ) {
        listener = eventListener;

        adView = new AdManagerAdView(context);
        adView.setAdSizes(mapToGamAdSizes(adSizes));
        adView.setAdUnitId(gamAdUnit);
        adView.setAdListener(this);
        adView.setAppEventListener(this);
        adView.setAdListener(this);
    }

    @Nullable
    static PublisherAdViewWrapper newInstance(Context context, String gamAdUnitId,
                                              GamAdEventListener eventListener, AdSize... adSizes) {
        try {
            return new PublisherAdViewWrapper(context,
                                              gamAdUnitId,
                                              eventListener,
                                              adSizes);
        }
        catch (Throwable throwable) {
            LogUtil.error(TAG, Log.getStackTraceString(throwable));
        }
        return null;
    }

    //region ==================== GAM AppEventsListener Implementation
    @Override
    public void onAppEvent(
        @NonNull
            String name,
        @NonNull
            String info) {
        if (Constants.APP_EVENT.equals(name)) {
            listener.onEvent(AdEvent.APP_EVENT_RECEIVED);
        }
    }
    //endregion ==================== GAM AppEventsListener Implementation

    //region ==================== GAM AdEventListener Implementation
    @Override
    public void onAdClosed() {
        listener.onEvent(AdEvent.CLOSED);
    }

    @Override
    public void onAdFailedToLoad(
        @NonNull
            LoadAdError loadAdError) {
        final AdEvent adEvent = AdEvent.FAILED;
        adEvent.setErrorCode(loadAdError.getCode());
        LogUtil.error("GoogleAds", loadAdError.getMessage());
        listener.onEvent(adEvent);
    }

    @Override
    public void onAdOpened() {
        listener.onEvent(AdEvent.CLICKED);
    }

    @Override
    public void onAdLoaded() {
        listener.onEvent(AdEvent.LOADED);
    }
    //endregion ==================== GAM AdEventListener Implementation

    public void loadAd(Bid bid) {
        try {
            AdManagerAdRequest adRequest = new AdManagerAdRequest.Builder().build();

            if (bid != null) {
                Map<String, String> targetingMap = new HashMap<>(bid.getPrebid().getTargeting());
                GamUtils.handleGamCustomTargetingUpdate(adRequest, targetingMap);
            }

            adView.loadAd(adRequest);
        }
        catch (Throwable throwable) {
            LogUtil.error(TAG, Log.getStackTraceString(throwable));
        }
    }

    public void setManualImpressionsEnabled(boolean enabled) {
        try {
            adView.setManualImpressionsEnabled(enabled);
        }
        catch (Throwable throwable) {
            LogUtil.error(TAG, Log.getStackTraceString(throwable));
        }
    }

    public void recordManualImpression() {
        try {
            adView.recordManualImpression();
        }
        catch (Throwable throwable) {
            LogUtil.error(TAG, Log.getStackTraceString(throwable));
        }
    }

    public void destroy() {
        try {
            adView.destroy();
        }
        catch (Throwable throwable) {
            LogUtil.error(TAG, Log.getStackTraceString(throwable));
        }
    }

    public View getView() {
        return adView;
    }

    /**
     * Extracts the GAM click tracking URL from the proxy AdManagerAdView's rendered HTML.
     * GAM populates {@code %%CLICK_URL_UNESC%%} in the creative, which appears as the href
     * of an anchor with {@code id="exchange-ping-url"}.
     */
    public void extractGamClickUrl(@NonNull GamClickUrlListener callback) {
        try {
            WebView webView = findFirstWebView(adView);
            if (webView == null) {
                LogUtil.debug(TAG, "extractGamClickUrl: No WebView found inside AdManagerAdView");
                callback.onResult(null);
                return;
            }

            webView.evaluateJavascript(JS_EXTRACT_CLICK_URL, value -> {
                String clickUrl = parseJsString(value);
                LogUtil.debug(TAG, "extractGamClickUrl: " + clickUrl);
                callback.onResult(clickUrl);
            });
        } catch (Throwable throwable) {
            LogUtil.error(TAG, "extractGamClickUrl failed: " + Log.getStackTraceString(throwable));
            callback.onResult(null);
        }
    }

    @Nullable
    private static WebView findFirstWebView(@Nullable View view) {
        if (view instanceof WebView) {
            return (WebView) view;
        }
        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                WebView result = findFirstWebView(viewGroup.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * Parses a raw string returned by {@link WebView#evaluateJavascript},
     * stripping the JSON string quotes and unescaping Unicode ampersands.
     */
    @Nullable
    private static String parseJsString(@Nullable String jsValue) {
        if (jsValue == null || jsValue.equals("null") || jsValue.equals("\"\"")) {
            return null;
        }
        if (jsValue.startsWith("\"") && jsValue.endsWith("\"")) {
            jsValue = jsValue.substring(1, jsValue.length() - 1);
        }
        jsValue = jsValue != null ? jsValue.replace("\\u0026", "&") : "";
        return jsValue.isEmpty() ? null : jsValue;
    }

    public interface GamClickUrlListener {
        void onResult(@Nullable String clickUrl);
    }

    private com.google.android.gms.ads.AdSize[] mapToGamAdSizes(AdSize[] adSizes) {
        if (adSizes == null) {
            return new com.google.android.gms.ads.AdSize[0];
        }

        final com.google.android.gms.ads.AdSize[] gamAdSizeArray = new com.google.android.gms.ads.AdSize[adSizes.length];
        for (int i = 0; i < adSizes.length; i++) {
            final AdSize prebidAdSize = adSizes[i];
            gamAdSizeArray[i] = new com.google.android.gms.ads.AdSize(prebidAdSize.getWidth(), prebidAdSize.getHeight());
        }

        return gamAdSizeArray;
    }
}
