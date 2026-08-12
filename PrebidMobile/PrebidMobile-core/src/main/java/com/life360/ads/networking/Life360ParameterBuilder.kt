package com.life360.ads.networking

import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.networking.parameters.AdRequestInput
import org.prebid.mobile.rendering.networking.parameters.ParameterBuilder

class Life360ParameterBuilder(
    private val adConfiguration: AdUnitConfiguration
) : ParameterBuilder() {

    override fun appendBuilderParameters(adRequestInput: AdRequestInput) {
        val bidRequest = adRequestInput.bidRequest

        for (imp in bidRequest.imp) {
            imp.tagid = adConfiguration.configId
        }
    }
}
