package com.life360.ads.networking

import android.content.Context
import android.net.Uri
import org.prebid.mobile.rendering.networking.urlBuilder.URLPathBuilder

class Life360PathBuilder(
    private val context: Context,
    private val configId: String
) : URLPathBuilder() {
    override fun buildURLPath(domain: String): String {
        return appendCustomQueryParameters(NATIVO_ENDPOINT, Life360QueryParameterStore.all(context, configId))
    }

    companion object {
        const val NATIVO_ENDPOINT = "https://exchange.postrelease.com/esi.json?ntv_epid=54"

        /**
         * Returns [url] with [customParams] appended as query parameters. A custom parameter is
         * dropped if [url] already has a query parameter with the same name, so a misconfigured
         * custom parameter can't clobber a structural one like `ntv_epid`.
         */
        private fun appendCustomQueryParameters(url: String, customParams: Map<String, String>): String {
            if (customParams.isEmpty()) return url

            val uri = Uri.parse(url)
            val existingKeys = uri.queryParameterNames
            val additions = customParams.filterKeys { it !in existingKeys }.toSortedMap()
            if (additions.isEmpty()) return url

            val builder = uri.buildUpon()
            additions.forEach { (key, value) -> builder.appendQueryParameter(key, value) }
            return builder.build().toString()
        }
    }
}
