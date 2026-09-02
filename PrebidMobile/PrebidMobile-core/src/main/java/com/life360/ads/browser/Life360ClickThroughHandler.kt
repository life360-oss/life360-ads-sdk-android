package com.life360.ads.browser

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import org.prebid.mobile.LogUtil
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.rendering.mraid.methods.MraidUrlHandler
import org.prebid.mobile.rendering.utils.broadcast.local.BaseLocalBroadcastReceiver
import org.prebid.mobile.rendering.utils.constants.IntentActions
import org.prebid.mobile.rendering.views.webview.mraid.BaseJSInterface

private const val TAG = "Life360ClickThroughHandler"

/**
 * Opens an ad's click-through. If the url is http(s) we hand it straight to a Custom Tab; everything
 * else falls through to [MraidUrlHandler] and gets resolved the way it always has.
 */
class Life360ClickThroughHandler(
    private val context: Context,
    jsInterface: BaseJSInterface?,
) : MraidUrlHandler(context, jsInterface) {

    private val application = context.applicationContext as? Application

    /** Watches for the Custom Tab currently open, or null if none is. */
    private var closeWatcher: Application.ActivityLifecycleCallbacks? = null

    override fun open(url: String?, broadcastId: Int) {
        if (shouldLaunchCustomTab(url)) {
            try {
                CustomTabsIntent.Builder().build().intent.apply {
                    data = Uri.parse(url)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(this)
                }
                watchForClose(broadcastId)
                return
            } catch (e: ActivityNotFoundException) {
                LogUtil.error(TAG, "Could not open $url in a Custom Tab, falling back")
            }
        }

        super.open(url, broadcastId)
    }

    /**
     * Starts watching for the app coming back to the foreground, which is what we treat as
     * [broadcastId]'s click-through closing.
     */
    private fun watchForClose(broadcastId: Int) {
        val application = application ?: return

        val watcher = object : Application.ActivityLifecycleCallbacks {

            // Whether something has paused, i.e. covered the app, since that's what we need to see
            // before a resume actually means anything.
            private var covered = false

            override fun onActivityPaused(activity: Activity) {
                covered = true
            }

            override fun onActivityResumed(activity: Activity) {
                if (!covered) return

                application.unregisterActivityLifecycleCallbacks(this)
                closeWatcher = null
                BaseLocalBroadcastReceiver.sendLocalBroadcast(
                    application,
                    broadcastId.toLong(),
                    IntentActions.ACTION_BROWSER_CLOSE,
                )
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }

        stopWatching()
        closeWatcher = watcher
        application.registerActivityLifecycleCallbacks(watcher)
    }

    /** Drop the watch along with the ad, so a click-through that outlives it doesn't report anything. */
    override fun destroy() {
        stopWatching()
        super.destroy()
    }

    private fun stopWatching() {
        closeWatcher?.let { application?.unregisterActivityLifecycleCallbacks(it) }
        closeWatcher = null
    }

    private fun shouldLaunchCustomTab(url: String?): Boolean {
        // Only worth trying for an http(s) url a Custom Tab would actually take — deep links and the
        // SDK's own schemes go to the base handler regardless — and only when something's actually
        // installed on the device to serve Custom Tabs in the first place.
        return PrebidMobile.shouldUseCustomTabsForClickThrough() &&
                url?.startsWith(PrebidMobile.SCHEME_HTTP) == true &&
                CustomTabsClient.getPackageName(context, emptyList()) != null
    }
}
