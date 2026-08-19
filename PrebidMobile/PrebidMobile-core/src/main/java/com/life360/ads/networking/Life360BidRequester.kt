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
import com.life360.ads.bid.Life360BidExt
import com.life360.ads.bid.Life360BidResponse
import org.prebid.mobile.api.exceptions.NoBidException
import java.util.concurrent.atomic.AtomicBoolean

class Life360BidRequester : ExternalBidRequester {

    private val requestInProgress = AtomicBoolean(false)
    private var networkTask: BaseNetworkTask? = null

    override fun requestBids(
        adUnitConfiguration: AdUnitConfiguration,
        listener: ExternalBidRequesterListener
    ) {
        if (!requestInProgress.compareAndSet(false, true)) {
            listener.onComplete(null, AdException(AdException.INTERNAL_ERROR, "Life360 request already in progress."))
            return
        }

        val context = PrebidContextHolder.getContext()
        if (context == null) {
            finishWithError(listener, AdException(AdException.INIT_ERROR, "Context is null."))
            return
        }

        val builders = ArrayList<ParameterBuilder>()
        builders.add(BasicParameterBuilder(adUnitConfiguration, context.resources, ExternalViewerUtils.isBrowserActivityCallable(context)))
        builders.add(Life360GeoLocationParameterBuilder())
        builders.add(AppInfoParameterBuilder(adUnitConfiguration))
        builders.add(DeviceInfoParameterBuilder(adUnitConfiguration))
        builders.add(NetworkParameterBuilder())
        builders.add(UserConsentParameterBuilder())
        builders.add(Life360ParameterBuilder(adUnitConfiguration))

        val urlBuilder = URLBuilder(Life360PathBuilder(context, adUnitConfiguration.configId), builders, AdRequestInput())
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

                if (response.statusCode == HTTP_NO_CONTENT) {
                    listener.onComplete(null, null)
                    return
                }

                val body = response.responseString
                if (body.isNullOrBlank()) {
                    listener.onComplete(null, null)
                    return
                }

                val bidResponse = Life360BidResponse(body, adUnitConfiguration)
                if (bidResponse.hasParseError()) {
                    listener.onComplete(null, AdException(AdException.INTERNAL_ERROR, bidResponse.parseError))
                } else {
                    listener.onComplete(bidResponse, null)
                }
            }

            override fun onError(msg: String?, responseTime: Long) {
                finishWithError(listener, AdException(AdException.INTERNAL_ERROR, "Life360 request failed: ${msg ?: "Unknown error"}"))
            }

            override fun onErrorWithException(e: Exception?, responseTime: Long) {
                // A 204 surfaces as a NoBidException (thrown by BaseNetworkTask); forward it as-is so the
                // publisher sees a no-bid result rather than a generic internal error.
                if (e is NoBidException) {
                    finishWithError(listener, e)
                    return
                }
                finishWithError(listener, AdException(AdException.INTERNAL_ERROR, "Life360 request failed: ${e?.message ?: "Unknown exception"}"))
            }
        }

        networkTask = BaseNetworkTask(responseHandler)
        networkTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params)
    }

    override fun shouldRenderImmediately(response: BidResponse?): Boolean {
        val bid = response?.winningBid ?: return false
        return Life360BidExt.isOwnedOperated(bid)
    }

    private fun finishWithError(listener: ExternalBidRequesterListener, exception: AdException) {
        if (exception !is NoBidException) LogUtil.debug(TAG, exception.message)
        requestInProgress.set(false)
        networkTask = null
        listener.onComplete(null, exception)
    }

    companion object {
        private const val TAG = "Life360BidRequester"
        private const val REQUEST_NAME = "nativo_bid_request"
        private const val HTTP_NO_CONTENT = 204
    }
}
