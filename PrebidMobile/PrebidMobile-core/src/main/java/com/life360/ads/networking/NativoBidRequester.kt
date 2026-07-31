package com.life360.ads.networking

import android.os.AsyncTask
import org.prebid.mobile.LogUtil
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.interfaces.ExternalBidRequester
import org.prebid.mobile.rendering.bidding.interfaces.ExternalBidRequesterListener
import org.prebid.mobile.rendering.networking.BaseNetworkTask
import org.prebid.mobile.rendering.networking.ResponseHandler
import org.prebid.mobile.rendering.networking.parameters.AdRequestInput
import org.prebid.mobile.rendering.networking.parameters.AppInfoParameterBuilder
import org.prebid.mobile.rendering.networking.parameters.BasicParameterBuilder
import org.prebid.mobile.rendering.networking.parameters.DeviceInfoParameterBuilder
import org.prebid.mobile.rendering.networking.parameters.NetworkParameterBuilder
import org.prebid.mobile.rendering.networking.parameters.ParameterBuilder
import org.prebid.mobile.rendering.networking.parameters.UserConsentParameterBuilder
import org.prebid.mobile.rendering.networking.urlBuilder.URLBuilder
import org.prebid.mobile.rendering.sdk.PrebidContextHolder
import org.prebid.mobile.rendering.utils.helpers.ExternalViewerUtils
import org.prebid.mobile.rendering.utils.helpers.AppInfoManager
import com.life360.ads.bid.NativoBidExt
import com.life360.ads.bid.NativoBidResponse
import org.prebid.mobile.api.exceptions.NoBidException
import java.util.concurrent.atomic.AtomicBoolean

class NativoBidRequester : ExternalBidRequester {

    private val requestInProgress = AtomicBoolean(false)
    private var networkTask: BaseNetworkTask? = null

    /**
     * Set by [cancel] and checked in every [ResponseHandler] callback.
     *
     * `AsyncTask.cancel(true)` does not suppress an `onPostExecute` that is already posted, so cancelling
     * the task alone cannot prevent a late callback — the flag is what does.
     */
    private val cancelled = AtomicBoolean(false)

    override fun requestBids(
        adUnitConfiguration: AdUnitConfiguration,
        listener: ExternalBidRequesterListener
    ) {
        if (!requestInProgress.compareAndSet(false, true)) {
            listener.onComplete(null, AdException(AdException.INTERNAL_ERROR, "Nativo request already in progress."))
            return
        }
        cancelled.set(false)

        val context = PrebidContextHolder.getContext()
        if (context == null) {
            finishWithError(listener, AdException(AdException.INIT_ERROR, "Context is null."))
            return
        }

        val builders = ArrayList<ParameterBuilder>()
        builders.add(BasicParameterBuilder(adUnitConfiguration, context.resources, ExternalViewerUtils.isBrowserActivityCallable(context)))
        builders.add(NativoGeoLocationParameterBuilder())
        builders.add(AppInfoParameterBuilder(adUnitConfiguration))
        builders.add(DeviceInfoParameterBuilder(adUnitConfiguration))
        builders.add(NetworkParameterBuilder())
        builders.add(UserConsentParameterBuilder())
        builders.add(NativoParameterBuilder(adUnitConfiguration))

        val urlBuilder = URLBuilder(NativoPathBuilder(), builders, AdRequestInput())
        val urlComponents = urlBuilder.buildUrl()

        val params = BaseNetworkTask.GetUrlParams().apply {
            url = urlComponents.baseUrl
            queryParams = urlComponents.queryArgString
            requestType = "POST"
            userAgent = AppInfoManager.getUserAgent()
            name = REQUEST_NAME
        }

        val responseHandler = object : ResponseHandler {
            override fun onResponse(response: BaseNetworkTask.GetUrlResult) {
                requestInProgress.set(false)
                networkTask = null
                if (cancelled.get()) {
                    LogUtil.debug(TAG, "Dropping Nativo response for a cancelled request.")
                    return
                }

                if (response.statusCode == HTTP_NO_CONTENT) {
                    listener.onComplete(null, null)
                    return
                }

                val body = response.responseString
                if (body.isNullOrBlank()) {
                    listener.onComplete(null, null)
                    return
                }

                val bidResponse = NativoBidResponse(body, adUnitConfiguration)
                if (bidResponse.hasParseError()) {
                    listener.onComplete(null, AdException(AdException.INTERNAL_ERROR, bidResponse.parseError))
                } else {
                    listener.onComplete(bidResponse, null)
                }
            }

            override fun onError(msg: String?, responseTime: Long) {
                finishWithError(listener, AdException(AdException.INTERNAL_ERROR, "Nativo request failed: ${msg ?: "Unknown error"}"))
            }

            override fun onErrorWithException(e: Exception?, responseTime: Long) {
                // A 204 surfaces as a NoBidException (thrown by BaseNetworkTask); forward it as-is so the
                // publisher sees a no-bid result rather than a generic internal error.
                if (e is NoBidException) {
                    finishWithError(listener, e)
                    return
                }
                finishWithError(listener, AdException(AdException.INTERNAL_ERROR, "Nativo request failed: ${e?.message ?: "Unknown exception"}"))
            }
        }

        networkTask = BaseNetworkTask(responseHandler)
        networkTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params)
    }

    override fun shouldRenderImmediately(response: BidResponse?): Boolean {
        val bid = response?.winningBid ?: return false
        return NativoBidExt.isOwnedOperated(bid)
    }

    /**
     * Cancels any in-flight request and detaches its callback. Call this whenever the requesting view is
     * destroyed or recycled, or the request stays alive to deliver a bid into a dead view.
     *
     * [BaseNetworkTask.destroy] is called directly, not just `cancel`, because it nulls the task's response
     * handler — that is what makes an already-queued `onPostExecute` return early instead of dispatching.
     */
    fun cancel() {
        cancelled.set(true)
        networkTask?.let {
            it.cancel(true)
            it.destroy()
        }
        networkTask = null
        requestInProgress.set(false)
    }

    private fun finishWithError(listener: ExternalBidRequesterListener, exception: AdException) {
        if (exception !is NoBidException) LogUtil.debug(TAG, exception.message)
        requestInProgress.set(false)
        networkTask = null
        if (cancelled.get()) {
            LogUtil.debug(TAG, "Dropping Nativo error for a cancelled request.")
            return
        }
        listener.onComplete(null, exception)
    }

    companion object {
        private const val TAG = "NativoBidRequester"
        private const val REQUEST_NAME = "nativo_bid_request"
        private const val HTTP_NO_CONTENT = 204
    }
}
