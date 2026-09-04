/*
 *    Copyright 2020-2021 Prebid.org, Inc.
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

package org.prebid.mobile;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.life360.ads.exposure.Life360CreativeVisibilityTracker;
import com.life360.ads.om.NativeOMResource;
import com.life360.ads.om.NativeOMUtils;
import org.prebid.mobile.rendering.models.internal.VisibilityTrackerOption;
import org.prebid.mobile.rendering.models.internal.VisibilityTrackerResult;
import org.prebid.mobile.rendering.models.ntv.NativeEventTracker;
import org.prebid.mobile.rendering.bidding.events.EventsNotifier;
import org.prebid.mobile.rendering.sdk.JSLibraryManager;
import org.prebid.mobile.rendering.session.manager.OmAdSessionManager;
import org.prebid.mobile.rendering.utils.helpers.ExternalViewerUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Response native ad object for all assets.
 */
public class PrebidNativeAd {

    private static final String TAG = "PrebidNativeAd";

    /** OpenRTB native `eventtrackers[].event` value for a plain impression. */
    private static final int IMPRESSION_EVENT_TYPE_ID = 1;

    private boolean impressionIsNotNotified = true;

    private final ArrayList<NativeTitle> titles = new ArrayList<>();
    private final ArrayList<NativeImage> images = new ArrayList<>();
    private final ArrayList<NativeData> dataList = new ArrayList<>();
    private String clickUrl;
    /**
     * The response's event trackers, grouped by the event type each one declared. Each group fires at its own
     * viewability threshold, so an `event: 1` pixel and an `event: 2` MRC50 pixel in the same response are no
     * longer both fired the moment the first threshold is met.
     */
    private final Map<NativeEventTracker.EventType, List<String>> eventTrackerUrls = new LinkedHashMap<>();
    @Nullable
    private ArrayList<String> click_trackers;
    private boolean expired;
    private WeakReference<View> registeredView;
    private PrebidNativeAdEventListener listener;
    private ArrayList<ClickTracker> clickTrackers;
    private String winEvent;
    private String impEvent;
    /** Resolved once at parse time, because the markup is not retained past {@link #create(String)}. */
    @Nullable
    private NativeOMResource omResource;
    @Nullable
    private OmAdSessionManager omAdSessionManager;
    /** Drives every viewability threshold this ad tracks, the OMID impression included. */
    @Nullable
    private Life360CreativeVisibilityTracker visibilityTracker;
    private boolean omImpressionTracked;
    @Nullable
    private String privacyUrl;


    public static PrebidNativeAd create(String cacheId) {
        String content = CacheManager.get(cacheId);
        if (!TextUtils.isEmpty(content)) {
            try {
                JSONObject details = new JSONObject(content);
                String admStr = details.getString("adm");
                JSONObject adm = new JSONObject(admStr);

                JSONObject nativeObj;
                if (adm.has("native")) {
                    nativeObj = adm.getJSONObject("native");
                } else {
                    nativeObj = adm;
                }

                JSONArray asset = nativeObj.getJSONArray("assets");
                final PrebidNativeAd ad = new PrebidNativeAd();
                CacheManager.registerCacheExpiryListener(cacheId, new CacheExpireListenerImpl(ad));
                for (int i = 0; i < asset.length(); i++) {
                    JSONObject adObject = asset.getJSONObject(i);
                    if (adObject.has("title")) {
                        JSONObject title = adObject.getJSONObject("title");
                        if (title.has("text")) {
                            String titleText = title.getString("text");
                            if (!titleText.isEmpty()) {
                                ad.addTitle(new NativeTitle(titleText));
                            }
                        } else {
                            LogUtil.warning(TAG, "Json title object doesn't have text field");
                        }
                    }
                    if (adObject.has("data")) {
                        JSONObject data = adObject.getJSONObject("data");

                        if (data.has("value")) {
                            int type = 0;
                            if (data.has("type")) {
                                type = data.optInt("type");
                            }
                            String value = data.getString("value");
                            ad.addData(new NativeData(type, value));
                        } else {
                            LogUtil.warning(TAG, "Json data object doesn't have type or value field");
                        }
                    }

                    if (adObject.has("img")) {
                        JSONObject img = adObject.getJSONObject("img");
                        if (img.has("url")) {
                            int type = 0;
                            if (img.has("type")) {
                                type = img.optInt("type");
                            }
                            String url = img.getString("url");
                            ad.addImage(new NativeImage(type, url));
                        } else {
                            LogUtil.warning(TAG, "Json image object doesn't have url or type field");
                        }
                    }
                }

                if (nativeObj.has("link")) {
                    JSONObject link = nativeObj.getJSONObject("link");
                    if (link.has("url")) {
                        String url = link.getString("url");
                        if (url.contains("{AUCTION_PRICE}") && details.has("price")) {
                            url = url.replace("{AUCTION_PRICE}", details.getString("price"));
                        }
                        ad.setClickUrl(url);
                    }

                    if (link.has("clicktrackers")) {
                        JSONArray clicktrackers = link.getJSONArray("clicktrackers");
                        if (clicktrackers.length() > 0) {
                            ad.click_trackers = new ArrayList<>();
                            for (int count = 0; count < clicktrackers.length(); count++) {
                                String clickTrackerUrl = clicktrackers.getString(count);
                                if (clickTrackerUrl.contains("{AUCTION_PRICE}") && details.has("price")) {
                                    clickTrackerUrl = clickTrackerUrl.replace("{AUCTION_PRICE}", details.getString("price"));
                                }
                                ad.click_trackers.add(clickTrackerUrl);
                            }
                        }
                    }
                }

                if (nativeObj.has("eventtrackers")) {
                    JSONArray eventtrackers = nativeObj.getJSONArray("eventtrackers");
                    for (int count = 0; count < eventtrackers.length(); count++) {
                        JSONObject eventtracker = eventtrackers.getJSONObject(count);
                        // An OMID tracker's url is a verification script for the OM SDK to load, not an
                        // image pixel — fetching it here would both miss the measurement and count as an
                        // impression the vendor never recorded. Every other tracker, JS ones included,
                        // stays on this path.
                        if (NativeOMUtils.isOmidEventTracker(eventtracker)) {
                            continue;
                        }
                        if (eventtracker.has("url")) {
                            String impUrl = eventtracker.getString("url");
                            if (impUrl.contains("{AUCTION_PRICE}") && details.has("price")) {
                                impUrl = impUrl.replace("{AUCTION_PRICE}", details.getString("price"));
                            }
                            ad.addEventTrackerUrl(
                                    eventTypeOf(eventtracker.optInt("event", IMPRESSION_EVENT_TYPE_ID)),
                                    impUrl
                            );
                        }
                    }
                }

                if (nativeObj.has("privacy")) {
                    String url = nativeObj.getString("privacy");
                    ad.setPrivacyUrl(url);
                }
                ad.omResource = NativeOMUtils.verificationResource(nativeObj);
                parseEvents(details, ad);
                if (ad.impEvent != null) {
                    ad.addEventTrackerUrl(NativeEventTracker.EventType.IMPRESSION, ad.impEvent);
                }
                return ad;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static void parseEvents(
            JSONObject bidJson,
            PrebidNativeAd ad
    ) {
        ad.winEvent = EventsNotifier.parseEvent("win", bidJson);
        ad.impEvent = EventsNotifier.parseEvent("imp", bidJson);
    }


    private PrebidNativeAd() {
    }

    public void addTitle(NativeTitle title) {
        titles.add(title);
    }

    public void addData(NativeData data) {
        dataList.add(data);
    }

    public void addImage(NativeImage image) {
        images.add(image);
    }

    @NonNull
    public ArrayList<NativeTitle> getTitles() {
        return titles;
    }

    @NonNull
    public ArrayList<NativeImage> getImages() {
        return images;
    }

    @NonNull
    public ArrayList<NativeData> getDataList() {
        return dataList;
    }

    public String getClickUrl() {
        return clickUrl;
    }

    private void setClickUrl(String clickUrl) {
        this.clickUrl = clickUrl;
    }

    /**
     * @return First title or empty string if it doesn't exist
     */
    @NonNull
    public String getTitle() {
        if (!titles.isEmpty()) {
            return titles.get(0).getText();
        }
        return "";
    }

    /**
     * @return First description data value or empty string if it doesn't exist
     */
    @NonNull
    public String getDescription() {
        for (NativeData data : dataList) {
            if (data.getType() == NativeData.Type.DESCRIPTION) {
                return data.getValue();
            }
        }
        return "";
    }

    /**
     * @return First icon url or empty string if it doesn't exist
     */
    @NonNull
    public String getIconUrl() {
        for (NativeImage image : images) {
            if (image.getType() == NativeImage.Type.ICON) {
                return image.getUrl();
            }
        }
        return "";
    }

    /**
     * @return First main image url or empty string if it doesn't exist
     */
    @NonNull
    public String getImageUrl() {
        for (NativeImage image : images) {
            if (image.getType() == NativeImage.Type.MAIN_IMAGE) {
                return image.getUrl();
            }
        }
        return "";
    }

    /**
     * @return First call to action data value or empty string if it doesn't exist
     */
    @NonNull
    public String getCallToAction() {
        for (NativeData data : dataList) {
            if (data.getType() == NativeData.Type.CALL_TO_ACTION) {
                return data.getValue();
            }
        }
        return "";
    }

    /**
     * @return First sponsored by data value or empty string if it doesn't exist
     */
    @NonNull
    public String getSponsoredBy() {
        for (NativeData data : dataList) {
            if (data.getType() == NativeData.Type.SPONSORED_BY) {
                return data.getValue();
            }
        }
        return "";
    }

    @Nullable
    public String getPrivacyUrl() {
        return privacyUrl;
    }

    private void setPrivacyUrl(@Nullable String url) {
        privacyUrl = url;
    }

    /**
     * This API is used to register the view for Ad Events (#onAdClicked(), #onAdImpression, #onAdExpired).
     *
     * @param container      the native ad container used to track impression
     * @param clickableViews list of views that should handle click
     * @param listener must not contain any references to View, Activity, because it can be in memory for a long time.
     *                 Should be class implementation and not anonymous object.
     *                 If it is anonymous class it can produce memory leak.
     * @return true if views registered successfully
     */
    public boolean registerView(View container, List<View> clickableViews, final PrebidNativeAdEventListener listener) {
        if (container == null || clickableViews == null || clickableViews.isEmpty()) {
            return false;
        }
        if (!expired && container != null) {
            this.listener = listener;

            setupOmSession(container);
            startViewabilityTracking(container);

            registeredView = new WeakReference<>(container);

            container.setOnClickListener(v -> handleClick(v, listener));

            if (clickableViews != null && clickableViews.size() > 0) {
                for (View views : clickableViews) {
                    if (views != null) {
                        views.setOnClickListener(v -> handleClick(v, listener));
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Starts an Open Measurement session against the view the app just registered.
     * <p>
     * Native display has no WebView for the OM SDK to instrument, so the session is handed the app-built
     * view directly. The session is declared {@link com.iab.omid.library.life360.adsession.ImpressionType#BEGIN_TO_RENDER},
     * so the impression is signalled here at render rather than on the viewability timer that gates the
     * pixel trackers — OMID's own script derives viewability from the registered view's geometry, which is
     * the measurement being delegated to it.
     */
    private void setupOmSession(View container) {
        if (omResource == null) {
            // Most demand carries no OM resource at all, so this is the normal path, not a failure.
            return;
        }

        OmAdSessionManager sessionManager = OmAdSessionManager.createNewInstance(
                JSLibraryManager.getInstance(container.getContext())
        );
        if (sessionManager == null) {
            LogUtil.error(TAG, "Open Measurement is unavailable, native display measurement will not be reported");
            return;
        }

        // Order is prescribed by the IAB integration guide: register the view, start, then signal.
        sessionManager.initNativeDisplayAdSession(omResource, null);
        sessionManager.registerAdView(container);
        sessionManager.startAdSession();
        sessionManager.displayAdLoaded();

        omAdSessionManager = sessionManager;
        LogUtil.debug(TAG, "Open Measurement native display session started, vendor " + omResource.getVendorKey());
    }

    /**
     * Starts viewability tracking for every threshold this ad needs: one per event type the response
     * declared, plus the impression threshold when Open Measurement is running, so the OMID impression has a
     * trigger even for a response that carries no impression pixel of its own.
     * <p>
     * A single tracker serves them all — it evaluates each option independently and reports which one fired,
     * so nothing has to reconcile several engines with different ideas of when the ad was seen.
     */
    private void startViewabilityTracking(View container) {
        Set<VisibilityTrackerOption> options = new LinkedHashSet<>();
        for (NativeEventTracker.EventType eventType : eventTrackerUrls.keySet()) {
            options.add(new VisibilityTrackerOption(eventType));
        }
        if (omAdSessionManager != null) {
            options.add(new VisibilityTrackerOption(NativeEventTracker.EventType.IMPRESSION));
        }

        if (options.isEmpty()) {
            return;
        }

        Life360CreativeVisibilityTracker tracker = new Life360CreativeVisibilityTracker(container, options);
        tracker.setVisibilityTrackerListener(this::onVisibilityEvent);
        tracker.startVisibilityCheck(container.getContext());
        visibilityTracker = tracker;
    }

    /**
     * Fires whatever the event type that just met its threshold is owed: the response's trackers for that
     * type, and on the impression threshold the OMID impression and the publisher callback.
     */
    @VisibleForTesting
    void onVisibilityEvent(VisibilityTrackerResult result) {
        if (!result.shouldFireImpression() || !result.isVisible()) {
            return;
        }

        NativeEventTracker.EventType eventType = result.getEventType();
        LogUtil.debug(TAG, "Viewability threshold met for " + eventType);

        fireEventTrackers(eventType);

        if (eventType == NativeEventTracker.EventType.IMPRESSION) {
            if (omAdSessionManager != null) {
                LogUtil.debug(TAG, "Open Measurement impression fired");
                omAdSessionManager.registerImpression();
            }
            if (listener != null) {
                listener.onAdImpression();
            }
        }
    }

    private void fireEventTrackers(NativeEventTracker.EventType eventType) {
        List<String> urls = eventTrackerUrls.get(eventType);
        if (urls == null) {
            return;
        }

        View view = registeredView != null ? registeredView.get() : null;
        if (view == null) {
            LogUtil.error(TAG, "Failed to fire event trackers for " + eventType + ". Registered view is gone");
            return;
        }

        for (String url : urls) {
            ImpressionTracker.createAndFire(url, view.getContext(), null);
        }
    }

    private void addEventTrackerUrl(NativeEventTracker.EventType eventType, String url) {
        List<String> urls = eventTrackerUrls.get(eventType);
        if (urls == null) {
            urls = new ArrayList<>();
            eventTrackerUrls.put(eventType, urls);
        }
        urls.add(url);
    }

    /**
     * Falls back to IMPRESSION for an event type the SDK has no threshold for, rather than dropping the
     * tracker — an unfired pixel is a lost impression, and IMPRESSION is the least presumptuous threshold.
     */
    private static NativeEventTracker.EventType eventTypeOf(int id) {
        NativeEventTracker.EventType eventType = NativeEventTracker.EventType.getType(id);
        if (eventType == null || eventType == NativeEventTracker.EventType.CUSTOM) {
            return NativeEventTracker.EventType.IMPRESSION;
        }
        return eventType;
    }

    /**
     * Ends the Open Measurement session. Without this the verification script keeps reporting on a view the
     * app has already discarded, so it must run on every teardown path.
     */
    private void stopOmSession() {
        if (visibilityTracker != null) {
            visibilityTracker.stopVisibilityCheck();
            visibilityTracker = null;
        }
        if (omAdSessionManager != null) {
            omAdSessionManager.stopAdSession();
            omAdSessionManager = null;
        }
    }

    /**
     * Releases the tracking this ad holds on the registered view. Apps that swap a native ad out of a reused
     * container should call this, otherwise the Open Measurement session outlives the ad it measures.
     */
    public void destroy() {
        stopOmSession();
        registeredView = null;
        listener = null;
    }

    private boolean handleClick(View v, PrebidNativeAdEventListener listener) {
        if (clickUrl == null || clickUrl.isEmpty()) {
            return false;
        }

        // open browser
        if (openNativeIntent(clickUrl, v.getContext())) {
            if (listener != null) {
                listener.onAdClicked();
            }
            fireClickTrackers(v.getContext());
            return true;
        }
        return false;
    }

    private boolean openNativeIntent(
            String url,
            Context context
    ) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ExternalViewerUtils.startActivity(context, intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    public String getWinEvent() {
        return winEvent;
    }

    public String getImpEvent() {
        return impEvent;
    }


    private void notifyImpressionEvent() {
        if (impressionIsNotNotified) {
            impressionIsNotNotified = false;
            EventsNotifier.notify(impEvent);
        }
    }

    private void fireClickTrackers(Context context) {
        if (click_trackers == null) {
            return;
        }
        for (String url: click_trackers) {
            ClickTracker.createAndFire(url, context, null);
        }
    }

    static class CacheExpireListenerImpl implements CacheManager.CacheExpiryListener {

        private PrebidNativeAd ad;

        public CacheExpireListenerImpl(PrebidNativeAd ad) {
            this.ad = ad;
        }

        @Override
        public void onCacheExpired() {
            LogUtil.error(TAG, "Cache expired");
            WeakReference<View> weakReference = ad.registeredView;
            if (weakReference == null) return;

            View view = weakReference.get();
            if (view != null) return;

            if (ad.listener != null) {
                ad.listener.onAdExpired();
            }
            ad.expired = true;
            ad.stopOmSession();
            ad.clickTrackers = null;
            ad.listener = null;
        }

    }

}
