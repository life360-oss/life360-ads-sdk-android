package com.life360.ads.browser

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import org.junit.After
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.rendering.utils.constants.IntentActions
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Life360ClickThroughHandlerTest {

    private companion object {
        const val CUSTOM_TABS_SERVICE_ACTION = "android.support.customtabs.action.CustomTabsService"
        const val BROADCAST_ID = 7
    }

    private lateinit var application: Application
    private lateinit var handler: Life360ClickThroughHandler

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        handler = Life360ClickThroughHandler(application, null)
    }

    @After
    fun tearDown() {
        PrebidMobile.setUseCustomTabsForClickThrough(false)
    }

    /**
     * Makes [android.content.pm.PackageManager] report back a browser that supports Custom Tabs.
     *
     * CustomTabsClient works in two steps: it looks up the default handler for an http VIEW intent,
     * then separately asks whether that same package also serves the Custom Tabs service. We have to
     * register both, or the lookup comes back empty and the handler falls straight through.
     */
    private fun installCustomTabsProvider(packageName: String = "com.example.browser") {
        val packageManager = shadowOf(application.packageManager)

        // Register the components themselves first, so the package actually exists as far as the
        // framework is concerned, then point the two lookups CustomTabsClient makes right at them.
        val browserActivity = ComponentName(packageName, "$packageName.BrowserActivity")
        val tabsService = ComponentName(packageName, "$packageName.CustomTabsService")
        packageManager.addActivityIfNotPresent(browserActivity)
        packageManager.addServiceIfNotPresent(tabsService)

        packageManager.addIntentFilterForActivity(
            browserActivity,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("http")
                addDataScheme("https")
            },
        )
        packageManager.addIntentFilterForService(
            tabsService,
            IntentFilter(CUSTOM_TABS_SERVICE_ACTION),
        )
    }

    /**
     * Drives the lifecycle the handler is actually waiting on, standing in for the user coming back
     * from wherever the click sent them.
     *
     * A bare resume on its own isn't enough to arm the report — that's why we pause first, matching
     * what the handler pairs a resume against. The idle at the end is what actually delivers it:
     * `LocalBroadcastManager` posts the broadcast to the main looper instead of sending it right away,
     * and Robolectric will just sit on that posted message until we tell the looper to idle.
     */
    private fun returnFromATab() {
        Robolectric.buildActivity(Activity::class.java).setup().pause().resume()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun lastStartedActivity(): Intent? = shadowOf(application).nextStartedActivity

    @Test
    fun `opens a custom tab when enabled`() {
        installCustomTabsProvider()
        PrebidMobile.setUseCustomTabsForClickThrough(true)

        handler.open("https://example.com/offer", 1)

        val started = lastStartedActivity()
        assertNotNull("expected a Custom Tab to be launched", started)
        assertEquals(Intent.ACTION_VIEW, started!!.action)
        assertEquals("https://example.com/offer", started.data.toString())
    }

    @Test
    fun `adds a new task flag when the context is not an activity`() {
        installCustomTabsProvider()
        PrebidMobile.setUseCustomTabsForClickThrough(true)

        handler.open("https://example.com/offer", 1)

        val flags = lastStartedActivity()?.flags ?: 0
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK,
            flags and Intent.FLAG_ACTIVITY_NEW_TASK,
        )
    }

    @Test
    fun `reports the click-through closed once the app is resumed`() {
        installCustomTabsProvider()
        PrebidMobile.setUseCustomTabsForClickThrough(true)
        val closed = AtomicBoolean(false)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                closed.set(true)
            }
        }
        LocalBroadcastManager.getInstance(application)
            .registerReceiver(receiver, IntentFilter(IntentActions.ACTION_BROWSER_CLOSE))

        handler.open("https://example.com/offer", BROADCAST_ID)
        assertFalse("nothing should be reported while the tab is still open", closed.get())

        returnFromATab()

        assertTrue("returning to the app should report the click-through closed", closed.get())
        LocalBroadcastManager.getInstance(application).unregisterReceiver(receiver)
    }

    @Test
    fun `reports nothing on the fallback path`() {
        // Custom Tabs are enabled, but nothing's installed to actually serve one, so this falls
        // through to the base handler — the same place a deep link would end up. That path already
        // reports its own close (or doesn't, for a deep link we can't see the end of), so we don't
        // arm anything for it ourselves — doing so would double-report the browser path.
        PrebidMobile.setUseCustomTabsForClickThrough(true)
        val closed = AtomicBoolean(false)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                closed.set(true)
            }
        }
        LocalBroadcastManager.getInstance(application)
            .registerReceiver(receiver, IntentFilter(IntentActions.ACTION_BROWSER_CLOSE))

        handler.open("https://example.com/offer", BROADCAST_ID)
        returnFromATab()

        assertFalse("the fallback path reports its own close, so we shouldn't", closed.get())
        LocalBroadcastManager.getInstance(application).unregisterReceiver(receiver)
    }

    @Test
    fun `reports nothing for a blank url`() {
        PrebidMobile.setUseCustomTabsForClickThrough(true)
        val closed = AtomicBoolean(false)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                closed.set(true)
            }
        }
        LocalBroadcastManager.getInstance(application)
            .registerReceiver(receiver, IntentFilter(IntentActions.ACTION_BROWSER_CLOSE))

        handler.open("", BROADCAST_ID)
        returnFromATab()

        assertFalse("nothing was ever going to open, so nothing should be watched", closed.get())
        LocalBroadcastManager.getInstance(application).unregisterReceiver(receiver)
    }

    @Test
    fun `does not open a custom tab when the flag is off`() {
        installCustomTabsProvider()

        handler.open("https://example.com/offer", 1)

        assertNull(lastStartedActivity())
    }

    @Test
    fun `leaves a deep link to the base handler`() {
        installCustomTabsProvider()
        PrebidMobile.setUseCustomTabsForClickThrough(true)

        handler.open("myapp://offer/12", 1)

        assertNull("a deep link must not reach a Custom Tab", lastStartedActivity())
    }

    @Test
    fun `leaves a deeplink plus url to the base handler`() {
        installCustomTabsProvider()
        PrebidMobile.setUseCustomTabsForClickThrough(true)

        handler.open("deeplink+://navigate?primaryUrl=myapp%3A%2F%2Foffer", 1)

        assertNull(lastStartedActivity())
    }

    @Test
    fun `leaves a null url to the base handler`() {
        installCustomTabsProvider()
        PrebidMobile.setUseCustomTabsForClickThrough(true)

        handler.open(null, 1)

        assertNull(lastStartedActivity())
    }

    @Test
    fun `does not open a custom tab when nothing supports them`() {
        PrebidMobile.setUseCustomTabsForClickThrough(true)

        handler.open("https://example.com/offer", 1)

        assertNull("no provider installed, so the click belongs to the base handler", lastStartedActivity())
    }
}
