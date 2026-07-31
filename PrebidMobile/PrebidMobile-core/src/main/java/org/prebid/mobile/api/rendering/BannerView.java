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
import org.prebid.mobile.api.data.VideoPlacementType;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.api.rendering.listeners.BannerVideoListener;
import org.prebid.mobile.api.rendering.listeners.BannerViewListener;
import org.prebid.mobile.api.rendering.listeners.NativoBannerViewListener;
import org.prebid.mobile.api.rendering.pluginrenderer.PluginEventListener;
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRegister;
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRenderer;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import com.life360.ads.core.R;
import org.prebid.mobile.rendering.bidding.data.bid.Bid;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.interfaces.BannerEventHandler;
import org.prebid.mobile.rendering.bidding.interfaces.StandaloneBannerEventHandler;
import com.life360.ads.Life360Ads;
import com.life360.ads.bid.NativoBidExt;
import com.life360.ads.bid.NativoBidResponse;
import com.life360.ads.bid.NativoAdType;
import com.life360.ads.server.NativoServerProxy;
import com.life360.ads.state.BannerLoadState;
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

    private NativoServerProxy nativoServer = new NativoServerProxy();

    @NonNull
    private BannerLoadState state = BannerLoadState.IDLE;

    @Nullable
    private NativoBidResponse nativoBidResponse;

    private final ScreenStateReceiver screenStateReceiver = new ScreenStateReceiver();

    @Nullable
    private BannerViewListener bannerViewListener;
    @Nullable
    private BannerVideoListener bannerVideoListener;

    private int refreshIntervalSec = 0;

    private String nativeStylesCreative = null;

    //region ==================== Listener implementation
    private final DisplayViewListener displayViewListener = new DisplayViewListener() {
        @Override
        public void onAdLoaded() {
            transitionTo(BannerLoadState.SHOWING);
            if (bannerViewListener == null) {
                return;
            }
            // Only route to the Nativo render path when the publisher opted in by implementing
            // NativoBannerViewListener. instanceof is R8-safe, unlike reflecting on an override.
            if (bannerViewListener instanceof NativoBannerViewListener && nativoDidRenderBid(winningBid)) {
                ((NativoBannerViewListener) bannerViewListener).onNativoAdLoaded(BannerView.this);
                return;
            }
            bannerViewListener.onAdLoaded(BannerView.this);
        }

        private boolean nativoDidRenderBid(BidResponse bidResponse) {
            if (bidResponse instanceof NativoBidResponse) {
                NativoAdType adType = NativoBidExt.getNativoAdType(bidResponse.getWinningBid());
                // Nativo rendering not used for type STANDARD_DISPLAY
                return adType != NativoAdType.STANDARD_DISPLAY;
            }
            return false;
        }

        @Override
        public void onAdDisplayed() {
            if (bannerViewListener != null) {
                bannerViewListener.onAdDisplayed(BannerView.this);
                eventHandler.trackImpression();
            }
        }

        @Override
        public void onAdFailed(AdException exception) {
            transitionTo(BannerLoadState.FAILED);
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
            // Resolve against the Nativo bid this cycle received, not the proxy's copy, which belongs to
            // whichever request started most recently.
            winningBid = nativoServer.decideWinner(response, nativoBidResponse);
            transitionTo(adServerStateFor(winningBid));
            eventHandler.requestAdWithBid(winningBid);
        }

        @Override
        public void onError(AdException exception) {
            Log.d(TAG, exception.toString());
            prebidException = exception;

            bidResponse = null;
            winningBid = nativoBidResponse;
            transitionTo(adServerStateFor(winningBid));
            eventHandler.requestAdWithBid(winningBid);
        }
    };

    private final BannerEventListener bannerEventListener = new BannerEventListener() {
        @Override
        public void onSdkWin(@Nullable BidResponse sdkBidResponse) {
            winningBid = sdkBidResponse;
            AdException parsedException = RenderingExceptionParser.getPrebidException(sdkBidResponse, prebidException);
            if (parsedException != null) {
                notifyErrorListener(parsedException);
                return;
            }

            displayAdWithBid(sdkBidResponse);
        }

        @Override
        public void onAdServerWin(View view) {
            notifyAdLoadedListener();
            displayAdServerView(view);
        }

        /**
         * Called only when third-party SDK (GAM) failed to load ad.
         */
        @Override
        public void onAdFailed(AdException gamException) {
            winningBid = nativoServer.decideWinner(bidResponse, nativoBidResponse);
            boolean prebidAlsoWithoutAd = RenderingExceptionParser.isBidInvalid(winningBid);
            if (prebidAlsoWithoutAd) {
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
     * Instantiates an BannerView with the ad details as an attribute.
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
     * Instantiates an BannerView for the given configId and adSize.
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
     * Instantiates an BannerView for GAM prebid integration.
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

        // The single gate for starting a cycle, covering every in-flight state and every entry point
        // (publisher call and refresh tick alike).
        if (!state.canStartLoad()) {
            LogUtil.debug(TAG, "loadAd: Skipped. Load already in progress (state=" + state + ").");
            return;
        }

        nativoBidResponse = null;
        transitionTo(BannerLoadState.LOADING);
        nativoServer.requestNativoBid(adUnitConfig, (bidResponse, shouldRenderImmediately, error) -> {
            // Completions arrive from AsyncTask.onPostExecute on the main looper, so one can land after
            // destroy(). Dropping it here is what stops a stale bid rendering into a torn-down view.
            if (state == BannerLoadState.DESTROYED) {
                LogUtil.debug(TAG, "Dropping Nativo bid response. BannerView is destroyed.");
                return null;
            }
            nativoBidResponse = bidResponse;

            if (shouldRenderImmediately && bidResponse != null) {
                // Start Nativo rendering
                bannerEventListener.onSdkWin(bidResponse);
            } else if (Life360Ads.isPrebidServerEnabled()) {
                // Start Prebid Server bid request
                transitionTo(BannerLoadState.LOADING);
                bidLoader.load();
            } else {
                prebidException = error;
                winningBid = bidResponse;
                transitionTo(adServerStateFor(winningBid));
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
        if (bidLoader != null) {
            bidLoader.cancelRefresh();
        }
    }

    /**
     * Cleans up resources when destroyed.
     */
    public void destroy() {
        // Mark the state terminal before tearing anything down, so a completion already queued on the main
        // looper is dropped rather than touching half-released state.
        state = BannerLoadState.DESTROYED;

        if (eventHandler != null) {
            eventHandler.destroy();
        }
        if (bidLoader != null) {
            bidLoader.destroy();
        }
        if (displayView != null) {
            displayView.destroy();
        }
        // The Nativo leg has its own in-flight request and must be cancelled explicitly.
        if (nativoServer != null) {
            nativoServer.destroy();
        }
        nativoBidResponse = null;
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

    public void setVideoPlacementType(VideoPlacementType videoPlacement) {
        adUnitConfig.setAdFormat(AdFormat.VAST);

        final PlacementType placementType = VideoPlacementType.mapToPlacementType(videoPlacement);
        adUnitConfig.setPlacementType(placementType);
    }

    @Nullable
    public VideoPlacementType getVideoPlacementType() {
        return VideoPlacementType.mapToVideoPlacementType(adUnitConfig.getPlacementTypeValue());
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
            // A cycle that ended without an ad may retry regardless of visibility, once: the state is consumed
            // here so the allowance does not persist across ticks.
            if (state == BannerLoadState.FAILED) {
                transitionTo(BannerLoadState.IDLE);
                return true;
            }

            final boolean isWindowVisibleToUser = screenStateReceiver.isScreenOn();
            return visibilityChecker.isVisibleForRefresh(this) && isWindowVisibleToUser;
        });

        // A refresh is a full load cycle, so it goes through loadAd() and is admitted by the same gate as a
        // publisher-initiated load.
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
        // Stay LOADING: the creative loads asynchronously, and only displayViewListener knows whether it
        // resolved. Reaching a load-admitting state here would let a refresh tick start a second cycle on top
        // of a render still in flight.
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

    private void notifyAdLoadedListener() {
        transitionTo(BannerLoadState.SHOWING);
        if (bannerViewListener != null) {
            bannerViewListener.onAdLoaded(BannerView.this);
        }
    }

    private void notifyErrorListener(AdException exception) {
        transitionTo(BannerLoadState.FAILED);
        LogUtil.debug(TAG, "Ad failed listener: " + exception);
        if (bannerViewListener != null) {
            bannerViewListener.onAdFailed(BannerView.this, exception);
        }
    }

    //region ==================== Load-cycle state machine

    /**
     * State to hold while the ad server works on {@code winningBid}.
     * <p>
     * With no bid to hand over, the ad server may still serve its own demand but this view is not waiting on
     * anything it can recognise, so the slot stays free for the next refresh rather than blocking on a callback
     * that may never come.
     */
    @NonNull
    private BannerLoadState adServerStateFor(@Nullable BidResponse winningBid) {
        return winningBid != null ? BannerLoadState.LOADING : BannerLoadState.IDLE;
    }

    /**
     * Records a state change. {@link BannerLoadState#DESTROYED} is terminal, which is what keeps a
     * callback that was already queued from resurrecting a torn-down view.
     *
     * @return false if the transition was refused.
     */
    private boolean transitionTo(@NonNull BannerLoadState next) {
        if (state == BannerLoadState.DESTROYED) {
            LogUtil.debug(TAG, "Ignoring transition to " + next + ". BannerView is destroyed.");
            return false;
        }
        state = next;
        return true;
    }

    //endregion ==================== Load-cycle state machine

        /**
     * Returns the winning bid response. This can be either a Prebid bid or a Nativo bid,
     * depending on which won the auction.
     *
     * @return the winning BidResponse (may be a NativoBidResponse), or null if no winner yet
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

    @VisibleForTesting
    final Bid getWinnerBid() {
        BidResponse response = nativoServer.decideWinner(bidResponse, nativoBidResponse);
        return nativoServer.getBidFromResponse(response);
    }

    @VisibleForTesting
    final boolean isPrimaryAdServerRequestInProgress() {
        return state == BannerLoadState.LOADING;
    }

    @VisibleForTesting
    @NonNull
    final BannerLoadState getLoadState() {
        return state;
    }

    /** Lets tests place the view in a mid-cycle state without driving the whole flow. */
    @VisibleForTesting
    final void setLoadStateForTest(@NonNull BannerLoadState state) {
        this.state = state;
    }
    //endregion ==================== HelperMethods for Unit Tests
}