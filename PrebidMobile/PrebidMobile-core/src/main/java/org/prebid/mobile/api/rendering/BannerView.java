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

package org.prebid.mobile.api.rendering;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import org.prebid.mobile.AdSize;
import org.prebid.mobile.LogUtil;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.data.AdUnitFormat;
import org.prebid.mobile.api.data.VideoPlacementType;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.api.rendering.listeners.BannerVideoListener;
import org.prebid.mobile.api.rendering.listeners.BannerViewListener;
import org.prebid.mobile.api.rendering.listeners.Life360BannerViewListener;
import org.prebid.mobile.api.rendering.pluginrenderer.PluginEventListener;
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRegister;
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRenderer;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import com.life360.ads.core.R;
import org.prebid.mobile.rendering.bidding.data.bid.Bid;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.interfaces.BannerEventHandler;
import org.prebid.mobile.rendering.bidding.interfaces.StandaloneBannerEventHandler;
import com.life360.ads.bid.AdServerType;
import com.life360.ads.bid.Life360BidExt;
import com.life360.ads.bid.Life360BidResponse;
import com.life360.ads.bid.Life360AdType;
import com.life360.ads.server.NativoServerProxy;
import org.prebid.mobile.rendering.bidding.listeners.BannerEventListener;
import org.prebid.mobile.rendering.bidding.listeners.BidRequesterListener;
import org.prebid.mobile.rendering.bidding.listeners.DisplayVideoListener;
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener;
import org.prebid.mobile.rendering.bidding.loader.BidLoader;
import org.prebid.mobile.rendering.models.AdPosition;
import org.prebid.mobile.rendering.models.PlacementType;
import org.prebid.mobile.rendering.models.internal.VisibilityTrackerOption;
import org.prebid.mobile.rendering.models.ntv.NativeEventTracker;
import org.prebid.mobile.rendering.utils.broadcast.ScreenStateReceiver;
import org.prebid.mobile.rendering.utils.helpers.RenderingExceptionParser;
import org.prebid.mobile.rendering.utils.helpers.VisibilityChecker;
import org.prebid.mobile.rendering.views.webview.mraid.Views;

import java.util.EnumSet;
import java.util.Set;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

/**
 * Ad view for banner ad with rendering API.
 */
public class BannerView extends FrameLayout {

    private final static String TAG = BannerView.class.getSimpleName();

    private final AdUnitConfiguration adUnitConfig = new AdUnitConfiguration();
    private BannerEventHandler eventHandler;

    private String configId;

    private DisplayView displayView;
    private BidLoader bidLoader;
    private BidResponse bidResponse;
    private BidResponse winningBid;
    private AdException prebidException;
    private final Handler expirationHandler = new Handler(Looper.getMainLooper());
    private Runnable expirationRunnable;
    private long bidExpirationUptimeMillis;
    private boolean isBidAdLoaded;
    private boolean expired;
    private boolean isRefreshStopped;

    private NativoServerProxy nativoServer = new NativoServerProxy();
    private boolean isLife360BidRequestInProgress;

    private final ScreenStateReceiver screenStateReceiver = new ScreenStateReceiver();

    @Nullable
    private BannerViewListener bannerViewListener;
    @Nullable
    private BannerVideoListener bannerVideoListener;

    private int refreshIntervalSec = 0;

    private boolean isPrimaryAdServerRequestInProgress;
    private boolean adFailed;

    // Which ad server actually served the last impression; null until the first auction resolves
    @Nullable
    private AdServerType adServer;

    private String nativeStylesCreative = null;

    /**
     * True once the publisher configured the formats through {@link #setAdUnitFormats(EnumSet)}.
     * Such an explicit choice takes precedence over the format implied by
     * {@link #setVideoPlacementType(VideoPlacementType)}.
     */
    private boolean adUnitFormatsConfigured;

    //region ==================== Listener implementation
    private final DisplayViewListener displayViewListener = new DisplayViewListener() {
        @Override
        public void onAdLoaded() {
            isBidAdLoaded = true;
            if (bannerViewListener == null) {
                return;
            }
            // Only route to the Life360 render path when the publisher opted in
            if (bannerViewListener instanceof Life360BannerViewListener && life360DidRenderBid(winningBid)) {
                ((Life360BannerViewListener) bannerViewListener).onLife360AdLoaded(BannerView.this);
                return;
            }
            bannerViewListener.onAdLoaded(BannerView.this);
            // The bid may have expired while the creative was loading.
            expireAdIfNeeded();
        }

        @Override
        public void onAdDisplayed() {
            // Once the impression is tracked, the bid can no longer expire.
            cancelExpiration();
            if (bannerViewListener != null) {
                bannerViewListener.onAdDisplayed(BannerView.this);
                eventHandler.trackImpression();
            }
        }

        @Override
        public void onAdFailed(AdException exception) {
            if (bannerViewListener != null) {
                bannerViewListener.onAdFailed(BannerView.this, exception);
            }
        }

        @Override
        public void onAdClicked() {
            eventHandler.trackClick();
            if (bannerViewListener != null) {
                bannerViewListener.onAdClicked(BannerView.this);
            }
        }

        @Override
        public void onAdClosed() {
            if (bannerViewListener != null) {
                bannerViewListener.onAdClosed(BannerView.this);
            }
        }
    };

    private final DisplayVideoListener displayVideoListener = new DisplayVideoListener() {
        @Override
        public void onVideoCompleted() {
            if (bannerVideoListener != null) {
                bannerVideoListener.onVideoCompleted(BannerView.this);
            }
        }

        @Override
        public void onVideoPaused() {
            if (bannerVideoListener != null) {
                bannerVideoListener.onVideoPaused(BannerView.this);
            }
        }

        @Override
        public void onVideoResumed() {
            if (bannerVideoListener != null) {
                bannerVideoListener.onVideoResumed(BannerView.this);
            }
        }

        @Override
        public void onVideoUnMuted() {
            if (bannerVideoListener != null) {
                bannerVideoListener.onVideoUnMuted(BannerView.this);
            }
        }

        @Override
        public void onVideoMuted() {
            if (bannerVideoListener != null) {
                bannerVideoListener.onVideoMuted(BannerView.this);
            }
        }
    };

    @Nullable
    private BidRequesterListener bidRequesterListener = new BidRequesterListener() {
        @Override
        public void onFetchCompleted(BidResponse response) {
            prebidException = null;
            bidResponse = response;

            // bid.exp counts from the auction, so the countdown starts as soon as the bid arrives.
            scheduleExpirationIfNeeded();

            winningBid = nativoServer.decideWinner(response);
            isPrimaryAdServerRequestInProgress = winningBid != null;
            eventHandler.requestAdWithBid(winningBid);
        }

        @Override
        public void onError(AdException exception) {
            Log.d(TAG, exception.toString());
            prebidException = exception;

            bidResponse = null;
            winningBid = nativoServer.getLife360BidResponse();
            isPrimaryAdServerRequestInProgress = winningBid != null;
            eventHandler.requestAdWithBid(winningBid);
        }
    };

    private final BannerEventListener bannerEventListener = new BannerEventListener() {
        @Override
        public void onSdkWin(@Nullable BidResponse sdkBidResponse) {
            markPrimaryAdRequestFinished();
            winningBid = sdkBidResponse;
            setAdServerWinner(winningBid == nativoServer.getLife360BidResponse() ? AdServerType.LIFE360 : AdServerType.PREBID);
            AdException parsedException = RenderingExceptionParser.getPrebidException(sdkBidResponse, prebidException);
            if (parsedException != null) {
                notifyErrorListener(parsedException);
                return;
            }

            displayAdWithBid(sdkBidResponse);
        }

        @Override
        public void onAdServerWin(View view) {
            markPrimaryAdRequestFinished();
            setAdServerWinner(AdServerType.GAM);
            notifyAdLoadedListener();
            displayAdServerView(view);
        }

        /**
         * Called only when third-party SDK (GAM) failed to load ad.
         */
        @Override
        public void onAdFailed(AdException gamException) {
            markPrimaryAdRequestFinished();
            winningBid = nativoServer.decideWinner(bidResponse);
            boolean prebidAlsoWithoutAd = RenderingExceptionParser.isBidInvalid(winningBid);
            if (prebidAlsoWithoutAd) {
                // Nobody actually served an impression: GAM failed and there's no valid fallback bid either.
                setAdServerWinner(null);
                AdException parsedException = RenderingExceptionParser.getPrebidException(winningBid, prebidException);
                String prebidStatus = parsedException != null ? parsedException.getMessage() : "Unknown";
                notifyErrorListener(new AdException(AdException.FAILED_TO_LOAD_BIDS, "GAM status: \"" + gamException.getMessage() + "\". Prebid status: \"" + prebidStatus + "\""));
                return;
            }

            onSdkWin(winningBid);
        }

        @Override
        public void onAdClicked() {
            if (bannerViewListener != null) {
                bannerViewListener.onAdClicked(BannerView.this);
            }
        }

        @Override
        public void onAdClosed() {
            if (bannerViewListener != null) {
                bannerViewListener.onAdClosed(BannerView.this);
            }
        }
    };
    //endregion ==================== Listener implementation

    /**
     * Instantiates a BannerView with the ad details as an attribute.
     *
     * @param attrs includes:
     *              <p>
     *              adUnitID
     *              refreshIntervalInSec
     */
    public BannerView(
        @NonNull
            Context context,
        @Nullable
            AttributeSet attrs
    ) {
        super(context, attrs);

        eventHandler = new StandaloneBannerEventHandler();
        reflectAttrs(attrs);
        init();
    }

    /**
     * Instantiates a BannerView for the given configId and adSize.
     */
    public BannerView(
        Context context,
        String configId,
        AdSize size
    ) {
        super(context);
        eventHandler = new StandaloneBannerEventHandler();
        this.configId = configId;
        adUnitConfig.addSize(size);

        init();
    }

    /**
     * Instantiates a BannerView for GAM prebid integration.
     */
    public BannerView(
        Context context,
        String configId,
        @NonNull
            BannerEventHandler eventHandler
    ) {
        super(context);
        this.eventHandler = eventHandler;
        this.configId = configId;

        init();
    }

    /**
     * Executes ad loading if no request is running.
     */
    public void loadAd() {
        if (bidLoader == null) {
            LogUtil.error(TAG, "loadAd: Failed. BidLoader is not initialized.");
            return;
        }

        if (isLife360BidRequestInProgress) {
            LogUtil.debug(TAG, "loadAd: Skipped. Life360 bid request is in progress.");
            return;
        }

        if (isPrimaryAdServerRequestInProgress) {
            LogUtil.debug(TAG, "loadAd: Skipped. Loading is in progress.");
            return;
        }

        // A successful load re-arms the BidLoader refresh timer.
        isRefreshStopped = false;

        isLife360BidRequestInProgress = true;
        nativoServer.requestLife360Bid(adUnitConfig, (bidResponse, shouldRenderImmediately, error) -> {
            isLife360BidRequestInProgress = false;

            if (shouldRenderImmediately && bidResponse != null) {
                isPrimaryAdServerRequestInProgress = false;
                // Start Life360 rendering
                bannerEventListener.onSdkWin(bidResponse);
            } else if (adUnitConfig.isPrebidServerEnabled()) {
                // Start Prebid Server bid request
                bidLoader.load();
            } else {
                prebidException = error;
                winningBid = nativoServer.getLife360BidResponse();
                isPrimaryAdServerRequestInProgress = winningBid != null;
                eventHandler.requestAdWithBid(winningBid);
                bidLoader.setupRefreshTimer();
            }

            return null;
        });

    }

    /**
     * Cancels BidLoader refresh timer.
     */
    public void stopRefresh() {
        isRefreshStopped = true;
        if (bidLoader != null) {
            bidLoader.cancelRefresh();
        }
    }

    /**
     * Cleans up resources when destroyed.
     */
    public void destroy() {
        if (eventHandler != null) {
            eventHandler.destroy();
        }
        if (bidLoader != null) {
            bidLoader.destroy();
        }
        if (displayView != null) {
            displayView.destroy();
        }
        cancelExpiration();
        bidRequesterListener = null;

        PrebidMobilePluginRegister.getInstance().unregisterEventListener(adUnitConfig.getFingerprint());

        screenStateReceiver.unregister();
    }

    //region ==================== getters and setters
    public void setAutoRefreshDelay(int seconds) {
        if (!adUnitConfig.isAdType(AdFormat.BANNER)) {
            LogUtil.info(TAG, "Autorefresh is available only for Banner ad type");
            return;
        }
        if (seconds < 0) {
            LogUtil.error(TAG, "setRefreshIntervalInSec: Failed. Refresh interval must be >= 0");
            return;
        }
        adUnitConfig.setAutoRefreshDelay(seconds);
    }

    public int getAutoRefreshDelayInMs() {
        return adUnitConfig.getAutoRefreshDelay();
    }

    public void addAdditionalSizes(AdSize... sizes) {
        adUnitConfig.addSizes(sizes);
    }

    public Set<AdSize> getAdditionalSizes() {
        return adUnitConfig.getSizes();
    }

    public void setBannerListener(BannerViewListener bannerListener) {
        bannerViewListener = bannerListener;
    }

    public void setBannerVideoListener(BannerVideoListener bannerVideoListener) {
        this.bannerVideoListener = bannerVideoListener;
    }

    public void setPluginEventListener(PluginEventListener pluginEventListener) {
        PrebidMobilePluginRegister.getInstance().registerEventListener(pluginEventListener, adUnitConfig.getFingerprint());
    }

    /**
     * Sets the video placement type and, unless the formats were explicitly configured through
     * {@link #setAdUnitFormats(EnumSet)}, switches the ad unit to a video only request.
     */
    public void setVideoPlacementType(VideoPlacementType videoPlacement) {
        if (!adUnitFormatsConfigured) {
            adUnitConfig.setAdFormat(AdFormat.VAST);
        }

        final PlacementType placementType = VideoPlacementType.mapToPlacementType(videoPlacement);
        adUnitConfig.setPlacementType(placementType);
    }

    @Nullable
    public VideoPlacementType getVideoPlacementType() {
        return VideoPlacementType.mapToVideoPlacementType(adUnitConfig.getPlacementTypeValue());
    }

    /**
     * Sets the ad unit formats requested on a single impression.
     * <p>
     * Defaults to {@link AdUnitFormat#BANNER}. Pass {@link AdUnitFormat#VIDEO} for an outstream
     * video banner, or both values to let display and video demand compete on the same impression.
     * <p>
     * A null or empty set is ignored and the current value is kept.
     * <p>
     * Auto refresh is deferred while a video creative is playing and resumes once playback
     * finishes, so a video is never torn down mid playback.
     */
    public void setAdUnitFormats(@Nullable EnumSet<AdUnitFormat> adUnitFormats) {
        if (adUnitFormats == null || adUnitFormats.isEmpty()) {
            LogUtil.warning(TAG, "Ad unit formats must contain at least one item. The current value is kept.");
            return;
        }

        adUnitConfig.setAdUnitFormats(adUnitFormats, false);
        adUnitFormatsConfigured = true;
    }

    @NonNull
    public EnumSet<AdUnitFormat> getAdUnitFormats() {
        return adUnitConfig.getAdUnitFormats();
    }

    /**
     * Sets BannerEventHandler for GAM prebid integration
     *
     * @param eventHandler instance of GamBannerEventHandler
     */
    public void setEventHandler(BannerEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    public void setAdPosition(AdPosition adPosition) {
        adUnitConfig.setAdPosition(adPosition);
    }

    public AdPosition getAdPosition() {
        return adUnitConfig.getAdPosition();
    }

    public void setPbAdSlot(String adSlot) {
        adUnitConfig.setPbAdSlot(adSlot);
    }

    @Nullable
    public String getPbAdSlot() {
        return adUnitConfig.getPbAdSlot();
    }

    /**
     * Sets the Global Placement ID (GPID) for this ad unit. The value is emitted as
     * {@code imp.ext.gpid} on the outgoing bid request.
     *
     * @param gpid the GPID to set, or {@code null} to leave it unset.
     */
    public void setGpid(@Nullable String gpid) {
        adUnitConfig.setGpid(gpid);
    }

    @Nullable
    public String getGpid() {
        return adUnitConfig.getGpid();
    }

    /**
     * Overrides the SDK-wide default ({@code Life360Ads.isPrebidServerEnabled}) for this
     * banner only. If switching to enable Prebid Server, please make sure you've called
     * {@code PrebidMobile.initializeSdk()} first.
     */
    public void setPrebidServerEnabled(boolean prebidServerEnabled) {
        adUnitConfig.setPrebidServerEnabled(prebidServerEnabled);
    }

    public boolean isPrebidServerEnabled() {
        return adUnitConfig.isPrebidServerEnabled();
    }

    /**
     * Which ad server actually served the last impression, or null before the first auction resolves.
     */
    @Nullable
    public AdServerType getAdServerWinner() {
        return adServer;
    }

    private void setAdServerWinner(@Nullable AdServerType adServer) {
        this.adServer = adServer;
    }

    //endregion ==================== getters and setters

    private void reflectAttrs(AttributeSet attrs) {
        if (attrs == null) {
            LogUtil.debug(TAG, "reflectAttrs. No attributes provided.");
            return;
        }
        TypedArray typedArray = getContext()
            .getTheme()
            .obtainStyledAttributes(attrs, R.styleable.BannerView, 0, 0);
        try {
            configId = typedArray.getString(R.styleable.BannerView_configId);
            refreshIntervalSec = typedArray.getInt(R.styleable.BannerView_refreshIntervalSec, 0);
            int width = typedArray.getInt(R.styleable.BannerView_adWidth, -1);
            int height = typedArray.getInt(R.styleable.BannerView_adHeight, -1);
            if (width >= 0 && height >= 0) {
                adUnitConfig.addSize(new AdSize(width, height));
            }
        } finally {
            typedArray.recycle();
        }
    }

    private void init() {
        initPrebidRenderingSdk();
        initAdConfiguration();
        initBidLoader();
        screenStateReceiver.register(getContext());
    }

    private void initPrebidRenderingSdk() {
        String hostUrl = PrebidMobile.getPrebidServerHost().getHostUrl();
        if (!hostUrl.isEmpty()) {
            PrebidMobile.initializeSdk(getContext(), hostUrl, null);
        }
    }

    private void initBidLoader() {
        bidLoader = new BidLoader(adUnitConfig, bidRequesterListener);
        final VisibilityTrackerOption visibilityTrackerOption = new VisibilityTrackerOption(NativeEventTracker.EventType.IMPRESSION);
        final VisibilityChecker visibilityChecker = new VisibilityChecker(visibilityTrackerOption);

        bidLoader.setBidRefreshListener(() -> {
            if (adFailed) {
                adFailed = false;
                return true;
            }

            // A video creative must not be torn down mid playback. The tick is skipped and the
            // timer rescheduled, so refreshing resumes once playback finishes.
            if (isVideoPlaying()) {
                return false;
            }

            final boolean isWindowVisibleToUser = screenStateReceiver.isScreenOn();
            return visibilityChecker.isVisibleForRefresh(this) && isWindowVisibleToUser;
        });

        // In serverless mode the refresh timer can't reload from Prebid Server; re-run the full
        // Life360 + event handler flow instead.
        bidLoader.setServerlessRefreshListener(this::loadAd);
    }

    private void initAdConfiguration() {
        adUnitConfig.setConfigId(configId);
        adUnitConfig.setAutoRefreshDelay(refreshIntervalSec);
        eventHandler.setBannerEventListener(bannerEventListener);
        adUnitConfig.setAdFormat(AdFormat.BANNER);
        adUnitConfig.addSizes(eventHandler.getAdSizeArray());
    }
    private void displayAdWithBid(BidResponse bidResponse) {
        if (indexOfChild(displayView) != -1) {
            displayView.destroy();
            displayView = null;
        }

        removeAllViews();

        displayView = new DisplayView(getContext(), displayViewListener, displayVideoListener, adUnitConfig, bidResponse);
        addView(displayView, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        // Give the PluginRenderer the final layout pass
        PrebidMobilePluginRenderer plugin = PrebidMobilePluginRegister.getInstance().getPluginForPreferredRenderer(bidResponse);
        if (plugin != null) {
            plugin.didInjectView(displayView, this, bidResponse);
        }
    }

    private void displayAdServerView(View view) {
        // The ad server's own creative is not the Prebid bid, so it never expires.
        resetExpiration();
        removeAllViews();

        if (view == null) {
            notifyErrorListener(new AdException(
                    AdException.INTERNAL_ERROR,
                    "Failed to displayAdServerView. Provided view is null"
            ));
            return;
        }

        Views.removeFromParent(view);
        addView(view);

        if (bannerViewListener != null) {
            bannerViewListener.onAdDisplayed(BannerView.this);
        }
    }

    /**
     * True while a Prebid video creative deployed by this view is playing.
     */
    @VisibleForTesting
    boolean isVideoPlaying() {
        return displayView != null && displayView.isVideoPlaying();
    }

    private void markPrimaryAdRequestFinished() {
        isPrimaryAdServerRequestInProgress = false;
    }

    private void notifyAdLoadedListener() {
        if (bannerViewListener != null) {
            bannerViewListener.onAdLoaded(BannerView.this);
        }
    }

    private void notifyErrorListener(AdException exception) {
        adFailed = true;
        LogUtil.debug(TAG, "Ad failed listener: " + exception);
        if (bannerViewListener != null) {
            bannerViewListener.onAdFailed(BannerView.this, exception);
        }
    }

    private boolean life360DidRenderBid(BidResponse bidResponse) {
        if (bidResponse instanceof Life360BidResponse) {
            Life360AdType adType = Life360BidExt.getLife360AdType(bidResponse.getWinningBid());
            // Life360 rendering not used for type STANDARD_DISPLAY
            return adType != Life360AdType.STANDARD_DISPLAY;
        }
        return false;
    }

    /**
     * Returns the winning bid response. This can be either a Prebid bid or a Life360 bid,
     * depending on which won the auction.
     *
     * @return the winning BidResponse (may be a Life360BidResponse), or null if no winner yet
     */
    @Nullable
    public BidResponse getBidResponse() {
        return winningBid;
    }

    @Nullable
    public String getImpOrtbConfig() {
        return adUnitConfig.getImpOrtbConfig();
    }

    /**
     * Sets imp level OpenRTB config JSON string that will be merged with the original imp object in the bid request.
     * Expected format: {@code "{"new_field": "value"}"}.
     * @param ortbConfig JSON config string.
     */
    public void setImpOrtbConfig(@Nullable String ortbConfig) {
        adUnitConfig.setImpOrtbConfig(ortbConfig);
    }

    @Nullable
    public String getGlobalOrtbConfig() {
        return adUnitConfig.getGlobalOrtbConfig();
    }

    /**
     * Sets the global OpenRTB configuration string for the ad unit. It takes precedence over `Targeting.setGlobalOrtbConfig`.
     * Expected format: {@code "{"new_field": "value"}"}.
     * @param ortbConfig The global OpenRTB JSON configuration string to set. Can be `null` to clear the configuration.
     */
    public void setGlobalOrtbConfig(@Nullable String ortbConfig) {
        adUnitConfig.setGlobalOrtbConfig(ortbConfig);
    }

    //region ==================== HelperMethods for Unit Tests. Should be used only in tests
    @VisibleForTesting
    final void setBidResponse(BidResponse response) {
        bidResponse = response;
    }

    private void scheduleExpirationIfNeeded() {
        // The new bid replaces the previous ad, so a bid without exp must not inherit its timer.
        resetExpiration();

        Integer expirationTimeSeconds = bidResponse != null ? bidResponse.getExpirationTimeSeconds() : null;
        // BidResponse normalizes absent, zero, and negative exp values to null.
        if (expirationTimeSeconds == null) {
            return;
        }

        bidExpirationUptimeMillis = SystemClock.uptimeMillis() + expirationTimeSeconds * 1000L;
        expirationRunnable = this::expireAdIfNeeded;
        expirationHandler.postAtTime(expirationRunnable, bidExpirationUptimeMillis);
    }

    private void cancelExpiration() {
        if (expirationRunnable != null) {
            expirationHandler.removeCallbacks(expirationRunnable);
            expirationRunnable = null;
        }
        bidExpirationUptimeMillis = 0;
    }

    private void resetExpiration() {
        cancelExpiration();
        isBidAdLoaded = false;
        expired = false;
    }

    private boolean hasBidExpired() {
        return bidExpirationUptimeMillis > 0 && SystemClock.uptimeMillis() >= bidExpirationUptimeMillis;
    }

    private void expireAdIfNeeded() {
        // Only the current bid's loaded Prebid ad can expire. While its creative loads,
        // onAdLoaded() re-checks, so onAdExpired never precedes onAdLoaded.
        if (expired || !isBidAdLoaded || !hasBidExpired()) {
            return;
        }

        expired = true;
        // A non-refreshable banner keeps showing the expired creative; the app is only notified.
        boolean isRefreshable = adUnitConfig.getAutoRefreshDelay() > 0 && !isRefreshStopped;
        if (isRefreshable) {
            if (displayView != null) {
                displayView.destroy();
                displayView = null;
            }
            removeAllViews();
        }

        if (bannerViewListener != null) {
            bannerViewListener.onAdExpired(BannerView.this);
        }

        if (isRefreshable) {
            loadAd();
        }
    }

    @VisibleForTesting
    final Bid getWinnerBid() {
        BidResponse response = nativoServer.decideWinner(bidResponse);
        return nativoServer.getBidFromResponse(response);
    }

    @VisibleForTesting
    final boolean isPrimaryAdServerRequestInProgress() {
        return isPrimaryAdServerRequestInProgress;
    }

    @VisibleForTesting
    final boolean isExpired() {
        return expired;
    }
    //endregion ==================== HelperMethods for Unit Tests
}