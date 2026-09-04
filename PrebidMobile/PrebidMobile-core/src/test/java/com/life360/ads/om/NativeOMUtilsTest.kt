package com.life360.ads.om

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Mirrors `Life360NativeOMUtilsTests` in the iOS SDK so the two platforms stay in step. */
@RunWith(RobolectricTestRunner::class)
class NativeOMUtilsTest {

    private val scriptUrl = "https://verification.example.com/omid.js"

    // region Phase 1: event trackers

    @Test
    fun eventTracker_omidEventTypeWithJsMethod() {
        val markup = JSONObject(
            """
            {"eventtrackers":[
              {"event":1,"method":1,"url":"https://example.com/pixel.gif"},
              {"event":555,"method":2,"url":"$scriptUrl","ext":{
                 "vendorKey":"vendor.com-omid","verificationParameters":"params-blob"}}
            ]}
            """.trimIndent()
        )

        assertEquals(
            NativeOMResource(scriptUrl, "vendor.com-omid", "params-blob"),
            NativeOMUtils.verificationResource(markup)
        )
    }

    @Test
    fun eventTracker_imageOnlyTrackersYieldNoResource() {
        val markup = JSONObject(
            """{"eventtrackers":[{"event":1,"method":1,"url":"https://example.com/pixel.gif"}]}"""
        )

        assertNull(NativeOMUtils.verificationResource(markup))
    }

    @Test
    fun eventTracker_missingVendorKeyYieldsNoResource() {
        val markup = JSONObject(
            """
            {"eventtrackers":[
              {"event":555,"method":2,"url":"$scriptUrl","ext":{"verificationParameters":"params-blob"}}
            ]}
            """.trimIndent()
        )

        assertNull(NativeOMUtils.verificationResource(markup))
    }

    @Test
    fun eventTracker_snakeCasedAndLowerCasedKeysResolve() {
        val markup = JSONObject(
            """
            {"eventtrackers":[
              {"event":555,"method":2,"ext":{
                 "omid_js_url":"$scriptUrl","vendor_key":"vendor.com-omid",
                 "verification_parameters":"params-blob"}}
            ]}
            """.trimIndent()
        )

        assertEquals("vendor.com-omid", NativeOMUtils.verificationResource(markup)?.vendorKey)
    }

    @Test
    fun eventTracker_firstCompleteTrackerWins() {
        val markup = JSONObject(
            """
            {"eventtrackers":[
              {"event":555,"method":2,"url":"$scriptUrl","ext":{"vendorKey":"incomplete"}},
              {"event":555,"method":2,"url":"$scriptUrl","ext":{
                 "vendorKey":"complete","verificationParameters":"params-blob"}}
            ]}
            """.trimIndent()
        )

        assertEquals("complete", NativeOMUtils.verificationResource(markup)?.vendorKey)
    }

    // endregion

    // region Phase 2: native.ext.omid

    @Test
    fun extOmid_singleObject() {
        val markup = JSONObject(
            """
            {"ext":{"omid":{
               "resourceUrl":"$scriptUrl","vendorKey":"vendor.com-omid","params":"params-blob"}}}
            """.trimIndent()
        )

        assertEquals(
            NativeOMResource(scriptUrl, "vendor.com-omid", "params-blob"),
            NativeOMUtils.verificationResource(markup)
        )
    }

    @Test
    fun extOmid_arrayOfObjects() {
        val markup = JSONObject(
            """
            {"ext":{"omid":[
               {"vendorKey":"incomplete"},
               {"url":"$scriptUrl","vendor":"vendor.com-omid","verificationParams":"params-blob"}
            ]}}
            """.trimIndent()
        )

        assertEquals("vendor.com-omid", NativeOMUtils.verificationResource(markup)?.vendorKey)
    }

    // endregion

    // region Phase 3: VAST-style AdVerifications

    @Test
    fun extAdVerifications() {
        val markup = JSONObject(
            """
            {"ext":{"adVerifications":{"verifications":[
               {"javascriptResourceUrl":"$scriptUrl","vendor":"vendor.com-omid",
                "verificationParameters":"params-blob"}
            ]}}}
            """.trimIndent()
        )

        assertEquals(
            NativeOMResource(scriptUrl, "vendor.com-omid", "params-blob"),
            NativeOMUtils.verificationResource(markup)
        )
    }

    @Test
    fun extVerificationsAtTopLevelOfExt() {
        val markup = JSONObject(
            """
            {"ext":{"verifications":[
               {"javascriptResourceUrl":"$scriptUrl","vendor":"vendor.com-omid",
                "verificationParameters":"params-blob"}
            ]}}
            """.trimIndent()
        )

        assertNotNull(NativeOMUtils.verificationResource(markup))
    }

    // endregion

    // region Phase ordering and rejection

    @Test
    fun eventTrackersWinOverExt() {
        val markup = JSONObject(
            """
            {"eventtrackers":[
               {"event":555,"method":2,"url":"$scriptUrl","ext":{
                  "vendorKey":"from-eventtrackers","verificationParameters":"params-blob"}}
             ],
             "ext":{"omid":{"url":"$scriptUrl","vendorKey":"from-ext","params":"params-blob"}}}
            """.trimIndent()
        )

        assertEquals("from-eventtrackers", NativeOMUtils.verificationResource(markup)?.vendorKey)
    }

    @Test
    fun urlWithoutHttpSchemeIsRejected() {
        for (url in listOf("not a url at all", "/relative/omid.js", "javascript:alert(1)", "ftp://x/omid.js")) {
            val markup = JSONObject(
                """{"ext":{"omid":{"url":"$url","vendorKey":"vendor.com-omid","params":"params-blob"}}}"""
            )

            assertNull("Expected $url to be rejected", NativeOMUtils.verificationResource(markup))
        }
    }

    @Test
    fun emptyStringsAreTreatedAsAbsent() {
        val markup = JSONObject(
            """{"ext":{"omid":{"url":"$scriptUrl","vendorKey":"","params":"params-blob"}}}"""
        )

        assertNull(NativeOMUtils.verificationResource(markup))
    }

    @Test
    fun markupWithoutAnyOMDataYieldsNoResource() {
        val markup = JSONObject("""{"link":{"url":"https://example.com/click"}}""")

        assertNull(NativeOMUtils.verificationResource(markup))
    }

    @Test
    fun nullMarkupYieldsNoResource() {
        assertNull(NativeOMUtils.verificationResource(null))
    }

    // endregion

    // region JS tracker classification

    @Test
    fun isOmidEventTracker_requiresJsMethod() {
        // event 555 is only an OMID resource when it is also a JS tracker.
        assertTrue(NativeOMUtils.isOmidEventTracker(JSONObject("""{"event":555,"method":2}""")))
        assertFalse(NativeOMUtils.isOmidEventTracker(JSONObject("""{"event":555,"method":1}""")))
        assertFalse(NativeOMUtils.isOmidEventTracker(JSONObject("""{}""")))
        assertFalse(NativeOMUtils.isOmidEventTracker(null))
    }

    /**
     * The conflation this guards against: a plain JS impression tracker is not a verification resource, and
     * treating it as one would lose the impression and give the OM SDK a script that reports nothing.
     */
    @Test
    fun isOmidEventTracker_plainJsImpressionTrackerIsNotOmid() {
        assertFalse(
            NativeOMUtils.isOmidEventTracker(
                JSONObject("""{"event":1,"method":2,"url":"https://example.com/imp.js"}""")
            )
        )
        // Nor is one whose ext carries something unrelated to verification.
        assertFalse(
            NativeOMUtils.isOmidEventTracker(
                JSONObject("""{"event":1,"method":2,"url":"https://e.com/i.js","ext":{"foo":"bar"}}""")
            )
        )
    }

    @Test
    fun isOmidEventTracker_viewabilityEventTypesAreNotOmid() {
        // MRC50 / MRC100 / video50 trackers must never be mistaken for verification resources.
        for (event in listOf(2, 3, 4)) {
            assertFalse(
                "event $event should not be treated as OMID",
                NativeOMUtils.isOmidEventTracker(
                    JSONObject("""{"event":$event,"method":2,"url":"https://e.com/v.js"}""")
                )
            )
        }
    }

    /** Tolerance for exchanges that put a complete resource on the generic impression event. */
    @Test
    fun isOmidEventTracker_completeVerificationExtOnImpressionEventIsOmid() {
        assertTrue(
            NativeOMUtils.isOmidEventTracker(
                JSONObject(
                    """{"event":1,"method":2,"url":"$scriptUrl","ext":{
                         "vendorKey":"v","verificationParameters":"p"}}"""
                )
            )
        )
    }

    @Test
    fun eventTracker_explicit555OutranksExtRecognisedTracker() {
        val markup = JSONObject(
            """
            {"eventtrackers":[
              {"event":1,"method":2,"url":"$scriptUrl","ext":{
                 "vendorKey":"from-ext-shape","verificationParameters":"params-blob"}},
              {"event":555,"method":2,"url":"$scriptUrl","ext":{
                 "vendorKey":"from-event-555","verificationParameters":"params-blob"}}
            ]}
            """.trimIndent()
        )

        assertEquals("from-event-555", NativeOMUtils.verificationResource(markup)?.vendorKey)
    }

    @Test
    fun eventTracker_nonOmidJsTrackerYieldsNoResource() {
        val markup = JSONObject(
            """{"eventtrackers":[{"event":1,"method":2,"url":"https://example.com/imp.js"}]}"""
        )

        assertNull(NativeOMUtils.verificationResource(markup))
    }

    // endregion

    // region Local dev server fixture

    /**
     * Guards the local test setup rather than the SDK: this is the `adm` the local Prebid Server serves for
     * the demo app's native slot. If the fixture and the extractor drift apart, the OM session silently never
     * starts on device, which is slow to diagnose.
     */
    @Test
    fun localDevServerNativeFixtureYieldsValidationResource() {
        val adm = """
            {"ver":"1.2","assets":[{"id":0,"title":{"text":"Prebid Server Native Ad"}},{"id":3,"data":{"type":1,"value":"Sponsored by Prebid.org"}}],"link":{"url":"https://prebid.org"},"eventtrackers":[{"event":1,"method":1,"url":"https://example.com/imp-tracker.png"},{"event":555,"method":2,"url":"https://compliance.iabtechnologylab.com/compliance-js/omid-validation-verification-script-v1.js","ext":{"vendorKey":"iabtechlab.com-omid","verification_parameters":"unused-by-validation-script"}}]}
        """.trimIndent()

        val resource = NativeOMUtils.verificationResource(JSONObject(adm))

        assertEquals(
            "https://compliance.iabtechnologylab.com/compliance-js/omid-validation-verification-script-v1.js",
            resource?.url
        )
        // The validation script filters session events on this exact vendor key.
        assertEquals("iabtechlab.com-omid", resource?.vendorKey)
        assertEquals("unused-by-validation-script", resource?.verificationParameters)
    }

    // endregion
}
