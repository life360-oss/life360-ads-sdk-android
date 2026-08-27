package com.life360

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.life360.ads.Life360Ads
import com.life360.ads.PrebidMobile
import com.life360.demo.AdConfiguration
import com.life360.demo.AdDemoApp
import com.life360.demo.BannerAdSlotController
import com.life360.demo.AdSlotController
import com.life360.demo.Life360VideoAdSlotController
import com.life360.demo.NativeAdSlotController
import com.life360.ui.theme.Life360AdsDemoTheme

private const val PREBID_ACCOUNT_ID = "test-account"

// BidPathBuilder POSTs directly to the host URL, so it must be the full auction endpoint
// (the SDK also derives /status by swapping this suffix).
private const val PREBID_SERVER_URL = "https://prebid-server.dev.life360.com/openrtb2/auction"

/**
 * Hosts one [AdSlotController] per format. They're owned by the Activity rather than by
 * composition so that switching tabs — which disposes the Compose subtree — can't destroy a live ad
 * view or trigger a second bid request for a slot that already has one.
 */
class MainActivity : ComponentActivity() {

    private lateinit var controllers: Map<AdConfiguration, AdSlotController>

    // Snapshot state read inside setContent. The init callback lands on the main thread.
    private val sdkReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        controllers = mapOf(
            AdConfiguration.BANNER to BannerAdSlotController(this),
            AdConfiguration.VIDEO to Life360VideoAdSlotController(this),
            AdConfiguration.NATIVE to NativeAdSlotController(this),
        )

        // The SDK must know the account id and server before any BannerView is created, since
        // BannerView reads the configured host during its own init. Initialization is asynchronous;
        // only once it reports back is it safe to load (the Prebid Server step checks that flag).
        PrebidMobile.setPrebidServerAccountId(PREBID_ACCOUNT_ID)
        PrebidMobile.initializeSdk(applicationContext, PREBID_SERVER_URL) { status ->
            Log.d(TAG, "Ads SDK init: $status")
            sdkReady.value = true
        }

        logSdkVersions()

        setContent {
            Life360AdsDemoTheme {
                AdDemoApp(controllers = controllers, sdkReady = sdkReady.value)
            }
        }
    }

    override fun onDestroy() {
        controllers.values.forEach(AdSlotController::destroy)
        super.onDestroy()
    }

    private fun logSdkVersions() {
        Log.d(TAG, "Life360 Ads SDK version: ${Life360Ads.version}")
        Log.d(TAG, "Prebid SDK version: ${Life360Ads.prebidVersion}")
    }

    private companion object {
        const val TAG = "Life360AdsDemo"
    }
}
