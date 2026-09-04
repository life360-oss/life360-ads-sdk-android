package com.life360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.life360.demo.TabConfiguration
import com.life360.demo.AdDemoApp
import com.life360.demo.AdsSDKInitializer
import com.life360.demo.BannerAdSlotController
import com.life360.demo.AdSlotController
import com.life360.demo.Life360VideoAdSlotController
import com.life360.demo.NativeAdSlotController
import com.life360.ui.theme.Life360AdsDemoTheme

/**
 * Hosts one [AdSlotController] per format. They're owned by the Activity rather than by
 * composition so that switching tabs — which disposes the Compose subtree — can't destroy a live ad
 * view or trigger a second bid request for a slot that already has one.
 */
class MainActivity : ComponentActivity() {

    private lateinit var controllers: Map<TabConfiguration, AdSlotController>

    // Snapshot state read inside setContent. The init callback lands on the main thread.
    private val sdkReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        controllers = mapOf(
            TabConfiguration.BANNER to BannerAdSlotController(this),
            TabConfiguration.VIDEO to Life360VideoAdSlotController(this),
            TabConfiguration.NATIVE to NativeAdSlotController(this),
        )

        AdsSDKInitializer.initialize(applicationContext) { sdkReady.value = true }

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

    private companion object {
        const val TAG = "Life360AdsDemo"
    }
}
