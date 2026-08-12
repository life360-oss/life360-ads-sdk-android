package com.life360.ads.networking

import org.prebid.mobile.rendering.networking.urlBuilder.URLPathBuilder

class Life360PathBuilder : URLPathBuilder() {
    override fun buildURLPath(domain: String): String {
        return NATIVO_ENDPOINT
    }

    companion object {
        const val NATIVO_ENDPOINT = "https://exchange.postrelease.com/esi.json?ntv_epid=54"
    }
}
