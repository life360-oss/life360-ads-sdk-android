package org.prebid.mobile.rendering.models.openrtb.bidRequests;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.data.Position;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import com.life360.ads.core.BuildConfig;

import static org.junit.Assert.*;

public class MobileSdkPassThroughTest {

    /** Mirrors PrebidMobile's private DEFAULT_BANNER_TIMEOUT / DEFAULT_PRERENDER_TIMEOUT. */
    private static final int DEFAULT_BANNER_TIMEOUT = 6 * 1000;
    private static final int DEFAULT_PRERENDER_TIMEOUT = 30 * 1000;

    @Test
    public void create_putWrongJsonObject_returnNull() throws JSONException {
        JSONObject jsonObject = new JSONObject("{}");

        MobileSdkPassThrough subject = MobileSdkPassThrough.create(jsonObject);

        assertNull(subject);
    }

    @Test
    public void create_putObjectWithWrongType_returnNull() throws JSONException {
        JSONObject jsonObject = new JSONObject("{\"prebid\":{\"passthrough\":[{\"type\":\"any\"}]}}");

        MobileSdkPassThrough subject = MobileSdkPassThrough.create(jsonObject);

        assertNull(subject);
    }

    @Test
    public void create_putObjectWithoutAdConfigurationOrSDKConfig_returnNull() throws JSONException {
        JSONObject jsonObject = new JSONObject("{\"prebid\":{\"passthrough\":[{\"type\":\"prebidmobilesdk\"}]}}");

        MobileSdkPassThrough subject = MobileSdkPassThrough.create(jsonObject);

        assertNull(subject);
    }

    @Test
    public void create_putObjectWithEmptyAdConfiguration_returnEmptyObject() throws JSONException {
        JSONObject jsonObject = new JSONObject(
            "{\"prebid\":{\"passthrough\":[{\"type\":\"prebidmobilesdk\", \"adconfiguration\":{}}]}}");

        MobileSdkPassThrough subject = MobileSdkPassThrough.create(jsonObject);

        assertNotNull(subject);

        assertNull(subject.isMuted);
        assertNull(subject.maxVideoDuration);
        assertNull(subject.skipDelay);
        assertNull(subject.skipButtonPosition);
        assertNull(subject.skipButtonArea);
        assertNull(subject.closeButtonArea);
        assertNull(subject.closeButtonPosition);
    }

    @Test
    public void create_putObjectWithAdConfiguration_returnFullObject() throws JSONException {
        JSONObject jsonObject = new JSONObject(
            "{\"prebid\":{\"passthrough\":[{\"type\":\"prebidmobilesdk\",\"adconfiguration\":{\n\"ismuted\": false,\n\"maxvideoduration\": 15,\n\"closebuttonarea\": 0.3,\n\"closebuttonposition\": \"topleft\",\n\"skipbuttonarea\": 0.3,\n\"skipbuttonposition\": \"topleft\",\n\"skipdelay\": 0}}]}}");

        MobileSdkPassThrough subject = MobileSdkPassThrough.create(jsonObject);

        assertNotNull(subject);

        assertEquals(false, subject.isMuted);
        assertEquals((Integer) 15, subject.maxVideoDuration);
        assertEquals((Integer) 0, subject.skipDelay);
        assertEquals(Position.TOP_LEFT, subject.skipButtonPosition);
        assertEquals((Double) 0.3, subject.skipButtonArea);
        assertEquals((Double) 0.3, subject.closeButtonArea);
        assertEquals(Position.TOP_LEFT, subject.closeButtonPosition);
    }

    @Test
    public void modifyAdUnitConfiguration_putObjectWithAdConfiguration_getModifiedAdUnitConfiguration() throws JSONException {
        JSONObject jsonObject = new JSONObject(
            "{\"prebid\":{\"passthrough\":[{\"type\":\"prebidmobilesdk\",\"adconfiguration\":{\n\"ismuted\": false,\n\"maxvideoduration\": 15,\n\"closebuttonarea\": 0.3,\n\"closebuttonposition\": \"topleft\",\n\"skipbuttonarea\": 0.3,\n\"skipbuttonposition\": \"topleft\",\n\"skipdelay\": 0}}]}}");

        MobileSdkPassThrough subject = MobileSdkPassThrough.create(jsonObject);
        AdUnitConfiguration adUnitConfiguration = new AdUnitConfiguration();

        assertNotNull(subject);

        subject.modifyAdUnitConfiguration(adUnitConfiguration);

        assertFalse(adUnitConfiguration.isMuted());
        assertEquals((Integer) 15, adUnitConfiguration.getMaxVideoDuration());
        assertEquals(0, adUnitConfiguration.getSkipDelay());
        assertEquals(Position.TOP_LEFT, adUnitConfiguration.getSkipButtonPosition());
        assertEquals(0.3, adUnitConfiguration.getSkipButtonArea(), 0);
        assertEquals(0.3, adUnitConfiguration.getCloseButtonArea(), 0);
        assertEquals(Position.TOP_LEFT, adUnitConfiguration.getCloseButtonPosition());
    }

    @Test
    public void combine_checkFromBidPriority() throws JSONException {
        JSONObject fromBidJsonObject = new JSONObject(
            "{\"prebid\":{\"passthrough\":[{\"type\":\"prebidmobilesdk\",\"adconfiguration\":{\n\"ismuted\": false,\n\"closebuttonarea\": 0.1}}]}}");
        JSONObject fromRootJsonObject = new JSONObject(
            "{\"prebid\":{\"passthrough\":[{\"type\":\"prebidmobilesdk\",\"adconfiguration\":{\n\"maxvideoduration\": 15,\n\"closebuttonarea\": 0.2}}]}}");
        MobileSdkPassThrough fromBid = MobileSdkPassThrough.create(fromBidJsonObject);
        MobileSdkPassThrough fromRoot = MobileSdkPassThrough.create(fromRootJsonObject);

        MobileSdkPassThrough result = MobileSdkPassThrough.combine(fromBid, fromRoot);

        assertNotNull(result);

        /* Only in fromBid response */
        assertFalse(result.isMuted);
        /* Only in fromRoot response */
        assertEquals((Integer) 15, result.maxVideoDuration);
        /* In fromBid = 0.1, in fromRoot = 0.2, fromBid have higher priority, so must be 0.1 */
        assertEquals((Double) 0.1, result.closeButtonArea);
    }

    @Test
    public void create_putObjectWithSdkConfiguration_returnFullObject() throws JSONException {
        JSONObject jsonObject = new JSONObject(
                "{\"prebid\":{\"passthrough\":[{\"type\":\"prebidmobilesdk\", \n\"sdkconfiguration\": {\n\"cftbanner\": 7800, \n\"cftprerender\": 21000}}]}}");

        MobileSdkPassThrough subject = MobileSdkPassThrough.create(jsonObject);

        assertNotNull(subject);
        assertEquals((Integer) 7800, subject.bannerTimeout);
        assertEquals((Integer) 21000, subject.preRenderTimeout);
    }

    @Test
    public void create_putObjectWithSdkConfiguration_onlyBannerTimeout() throws JSONException {
        JSONObject jsonObject = new JSONObject(
                "{\"prebid\":{\"passthrough\":[{\"type\":\"prebidmobilesdk\", \n\"sdkconfiguration\": {\n\"cftbanner\": 7900}}]}}");

        MobileSdkPassThrough subject = MobileSdkPassThrough.create(jsonObject);

        assertNotNull(subject);
        assertEquals((Integer) 7900, subject.bannerTimeout);
    }

    @Test
    public void create_putObjectWithSdkConfiguration_onlyPreRenderTimeout() throws JSONException {
        JSONObject jsonObject = new JSONObject(
                "{\"prebid\":{\"passthrough\":[{\"type\":\"prebidmobilesdk\", \n\"sdkconfiguration\": {\n\"cftprerender\": 22000}}]}}");

        MobileSdkPassThrough subject = MobileSdkPassThrough.create(jsonObject);

        assertNotNull(subject);
        assertEquals((Integer) 22000, subject.preRenderTimeout);
    }

    //region ==================== Server-supplied timeouts are scoped per ad unit

    private static final String SDK_CONFIG_JSON =
            "{\"prebid\":{\"passthrough\":[{\"type\":\"prebidmobilesdk\", \n\"sdkconfiguration\": {\n\"cftbanner\": 1, \n\"cftprerender\": 2}}]}}";

    @Before
    public void resetProcessWideTimeouts() {
        PrebidMobile.setPbsConfig(null);
        PrebidMobile.setCreativeFactoryTimeout(DEFAULT_BANNER_TIMEOUT);
        PrebidMobile.setCreativeFactoryTimeoutPreRenderContent(DEFAULT_PRERENDER_TIMEOUT);
    }

    @After
    public void clearProcessWideTimeouts() {
        resetProcessWideTimeouts();
    }

    /**
     * Parsing must not touch process-wide state. A write from the constructor would let one ad unit's response
     * reconfigure the render deadline of every ad unit in the process.
     */
    @Test
    public void create_putObjectWithSdkConfiguration_doesNotWriteProcessGlobal() throws JSONException {
        MobileSdkPassThrough subject = MobileSdkPassThrough.create(new JSONObject(SDK_CONFIG_JSON));

        assertNotNull(subject);
        assertNull(PrebidMobile.getPbsConfig());
        assertEquals(DEFAULT_BANNER_TIMEOUT, PrebidMobile.getCreativeFactoryTimeout());
        assertEquals(DEFAULT_PRERENDER_TIMEOUT, PrebidMobile.getCreativeFactoryTimeoutPreRenderContent());
    }

    @Test
    public void modifyAdUnitConfiguration_sdkConfigurationTimeouts_applyOnlyToTheOwningAdUnit()
    throws JSONException {
        MobileSdkPassThrough subject = MobileSdkPassThrough.create(new JSONObject(SDK_CONFIG_JSON));
        assertNotNull(subject);
        AdUnitConfiguration adUnitThatGotTheResponse = new AdUnitConfiguration();
        AdUnitConfiguration concurrentlyLoadingAdUnit = new AdUnitConfiguration();

        subject.modifyAdUnitConfiguration(adUnitThatGotTheResponse);

        assertEquals(1, adUnitThatGotTheResponse.getCreativeFactoryTimeoutMs());
        assertEquals(2, adUnitThatGotTheResponse.getCreativeFactoryTimeoutPreRenderMs());
        assertEquals(DEFAULT_BANNER_TIMEOUT, concurrentlyLoadingAdUnit.getCreativeFactoryTimeoutMs());
        assertEquals(DEFAULT_PRERENDER_TIMEOUT, concurrentlyLoadingAdUnit.getCreativeFactoryTimeoutPreRenderMs());
    }

    /** With no server-supplied value the ad unit falls back to the process-wide default. */
    @Test
    public void adUnitWithoutServerSuppliedTimeouts_fallsBackToProcessDefaults() {
        PrebidMobile.setCreativeFactoryTimeout(7000);
        PrebidMobile.setCreativeFactoryTimeoutPreRenderContent(25000);

        AdUnitConfiguration adUnitConfiguration = new AdUnitConfiguration();

        assertEquals(7000, adUnitConfiguration.getCreativeFactoryTimeoutMs());
        assertEquals(25000, adUnitConfiguration.getCreativeFactoryTimeoutPreRenderMs());
    }



    //endregion ==================== Server-supplied timeouts are scoped per ad unit

}