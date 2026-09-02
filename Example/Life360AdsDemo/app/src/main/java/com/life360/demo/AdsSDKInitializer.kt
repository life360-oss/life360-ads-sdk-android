package com.life360.demo

import android.content.Context
import android.util.Log
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.TargetingParams
import org.prebid.mobile.api.data.InitializationStatus

/**
 * Configures and initializes the Ads SDK against the Prebid Server selected below
 */
object AdsSDKInitializer {

    private const val ACCOUNT_ID = "test-account"

    // If using local Prebid development server
    private const val USE_LOCAL_HOST = true
    private const val USE_CHARLES = true
    private const val LIFE360_DEV_HOST = "prebid-server.dev.life360.com"

    private const val TAG = "Life360AdsDemo"

    fun initialize(context: Context, onComplete: (InitializationStatus) -> Unit) {
        applySharedConfiguration()
        initializeWithPrebidServer(context, onComplete)
    }

    private fun applySharedConfiguration() {
        PrebidMobile.setPbsDebug(true)
        PrebidMobile.setLogLevel(PrebidMobile.LogLevel.DEBUG)

        PrebidMobile.setPrebidServerAccountId(ACCOUNT_ID)

        TargetingParams.setPublisherName("Life360")
        TargetingParams.setStoreUrl("https://play.google.com/store/apps/details?id=com.nativo.mraidandroidapp")
    }

    private fun initializeWithPrebidServer(context: Context, onComplete: (InitializationStatus) -> Unit) {
        val host = prebidServerHost(USE_LOCAL_HOST, USE_CHARLES)
        PrebidMobile.setCustomStatusEndpoint("$host/status")

        PrebidMobile.initializeSdk(context, "$host/openrtb2/auction") { status ->
            when (status) {
                InitializationStatus.SUCCEEDED ->
                    Log.d(TAG, "PrebidSDK: initialized successfully")
                InitializationStatus.SERVER_STATUS_WARNING ->
                    Log.d(TAG, "PrebidSDK: init OK but PBS /status check warned – ${status.description}")
                InitializationStatus.FAILED ->
                    Log.d(TAG, "PrebidSDK: init FAILED – ${status.description}")
            }
            onComplete(status)
        }
    }

    fun prebidServerHost(useLocalHost: Boolean, useCharles: Boolean): String {
        val charlesHost = "localhost.charlesproxy.com:8001"
        val localHost = if (useCharles) charlesHost else "127.0.0.1:8001"
        val host = if (useLocalHost) localHost else LIFE360_DEV_HOST
        val prebidServerUrl = "http${if (useLocalHost) "" else "s"}://$host"
        return prebidServerUrl
    }
}
