package com.life360.ads

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.life360.ads.core.BuildConfig
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.rendering.listeners.SdkInitializationListener
import org.prebid.mobile.rendering.sdk.SdkInitializer

/**
 * Entry point for the Life360 Ads SDK.
 *
 * The SDK is a fork of Prebid Mobile, so most configuration still lives on `Prebid`/`Targeting`
 * because both the Prebid and Life360 ad paths share most of the request and render architecture.
 */
object Life360Ads {

    /** Life360 Ads SDK (product) version */
    @JvmField
    val version: String = BuildConfig.VERSION

    /** Upstream Prebid Mobile version */
    @JvmField
    val prebidVersion: String = BuildConfig.PREBID_VERSION

    /** Product name used as the demand `source` in outgoing bid requests. */
    @JvmField
    val sdkName: String = PrebidMobile.SDK_NAME

    /**
     * Whether the SDK was initialized with or without Prebid Server
     * [initializeWithoutPrebid] sets it off.
     */
    @JvmStatic
    @set:RestrictTo(RestrictTo.Scope.LIBRARY)
    var isPrebidServerEnabled: Boolean = true

    /**
     * Allows the SDK to share geolocation on Life360 bid requests if permission is granted by the user.
     */
    @JvmStatic
    var isShareGeoLocationWithLife360: Boolean = false

    /**
     * Initializes the SDK without a Prebid Server, for integrations that only use Life360 demand plus
     * their own ad-server event handler. Use this when you have no Prebid Server to point at: the
     * BannerView flow then skips the Prebid Server bid request entirely and goes straight from the
     * Life360 request to the EventHandler request. The PBS /status check is also skipped during init.
     *
     * @param context any context (must be not null)
     * @param listener initialization listener (can be null)
     */
    @JvmStatic
    @JvmOverloads
    @MainThread
    fun initializeWithoutPrebid(
        context: Context?,
        listener: SdkInitializationListener? = null
    ) {
        isPrebidServerEnabled = false
        SdkInitializer.init(context, listener)
    }
}
