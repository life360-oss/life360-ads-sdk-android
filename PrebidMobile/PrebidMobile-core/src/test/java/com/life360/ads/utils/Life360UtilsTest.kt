package com.life360.ads.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.net.URI

class Life360UtilsTest {

    /**
     * The shape a click redirect takes when an ad server leaves its consent macros unexpanded: path
     * segments delimited by semicolons, macros in the middle, and the destination appended as a query.
     */
    private val unexpandedMacroUrl =
        "https://adserver.example.com/click/N0.0PLACEMENT/B0.0" +
            ";trk_aid=0;gdpr=\${GDPR};gdpr_consent=\${GDPR_CONSENT_0};tdv=1" +
            "?https://landing.example.com/offer"

    private val clickTrackerUrl = "https://clickserver.example.com/click/0/0;0;0;0;0/"

    @Test
    fun `repairRedirectUrl encodes the braces an unexpanded macro leaves behind`() {
        val repaired = Life360Utils.repairRedirectUrl(unexpandedMacroUrl)

        assertEquals(
            "https://adserver.example.com/click/N0.0PLACEMENT/B0.0" +
                ";trk_aid=0;gdpr=\$%7BGDPR%7D;gdpr_consent=\$%7BGDPR_CONSENT_0%7D;tdv=1" +
                "?https://landing.example.com/offer",
            repaired,
        )
    }

    @Test
    fun `a repaired url resolves where the url it came from does not`() {
        try {
            URI(clickTrackerUrl).resolve(unexpandedMacroUrl)
            fail("Expected the unrepaired url to be rejected")
        } catch (expected: IllegalArgumentException) {
            // The rejection this repair exists for.
        }

        val resolved = URI(clickTrackerUrl).resolve(Life360Utils.repairRedirectUrl(unexpandedMacroUrl))

        assertEquals("adserver.example.com", resolved.host)
    }

    @Test
    fun `repairRedirectUrl leaves a url that needs no repair alone`() {
        val legal = "https://landing.example.com/offer?source=0"

        assertEquals(legal, Life360Utils.repairRedirectUrl(legal))
    }

    @Test
    fun `repairRedirectUrl passes null through`() {
        assertNull(Life360Utils.repairRedirectUrl(null))
    }
}
