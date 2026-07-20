package com.life360.ads.networking

import com.life360.ads.Life360Ads
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.rendering.networking.parameters.GeoLocationParameterBuilder

class NativoGeoLocationParameterBuilder : GeoLocationParameterBuilder() {
    override fun isEnabled(): Boolean =
        Life360Ads.isShareGeoLocationWithNativo || PrebidMobile.isShareGeoLocation()
}
