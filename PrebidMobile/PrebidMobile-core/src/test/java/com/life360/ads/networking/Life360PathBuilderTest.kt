package com.life360.ads.networking

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private const val CONFIG_ID = "test-config-id"
private const val OTHER_CONFIG_ID = "other-config-id"

/**
 * Covers [Life360PathBuilder]: the fixed endpoint, custom-query-parameter appending (sorting,
 * collision with the fixed `ntv_epid` param), and per-configId scoping via
 * [Life360QueryParameterStore].
 */
@RunWith(RobolectricTestRunner::class)
class Life360PathBuilderTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun buildURLPath_withNoStoredCustomParameters_returnsFixedEndpoint() {
        val path = Life360PathBuilder(context, CONFIG_ID).buildURLPath("ignored-domain")

        assertEquals(Life360PathBuilder.NATIVO_ENDPOINT, path)
    }

    @Test
    fun buildURLPath_withStoredCustomParameters_appendsThem() {
        writeCustomParams(CONFIG_ID, "publisher_id" to "abc123")

        val path = Life360PathBuilder(context, CONFIG_ID).buildURLPath("ignored-domain")

        assertEquals("${Life360PathBuilder.NATIVO_ENDPOINT}&publisher_id=abc123", path)
    }

    @Test
    fun buildURLPath_appendsMultipleParametersInSortedOrder() {
        writeCustomParams(CONFIG_ID, "b" to "2", "a" to "1")

        val path = Life360PathBuilder(context, CONFIG_ID).buildURLPath("ignored-domain")

        assertEquals("${Life360PathBuilder.NATIVO_ENDPOINT}&a=1&b=2", path)
    }

    @Test
    fun buildURLPath_cannotOverrideFixedParameter() {
        writeCustomParams(CONFIG_ID, "ntv_epid" to "999")

        val path = Life360PathBuilder(context, CONFIG_ID).buildURLPath("ignored-domain")

        assertEquals(Life360PathBuilder.NATIVO_ENDPOINT, path)
    }

    @Test
    fun buildURLPath_dropsOnlyTheCollidingKeyWhenMixedWithNewOnes() {
        writeCustomParams(CONFIG_ID, "ntv_epid" to "999", "publisher_id" to "abc123")

        val path = Life360PathBuilder(context, CONFIG_ID).buildURLPath("ignored-domain")

        assertEquals("${Life360PathBuilder.NATIVO_ENDPOINT}&publisher_id=abc123", path)
    }

    @Test
    fun buildURLPath_onlyAppendsParametersStoredForItsOwnConfigId() {
        writeCustomParams(CONFIG_ID, "publisher_id" to "abc123")
        writeCustomParams(OTHER_CONFIG_ID, "publisher_id" to "should-not-appear")

        val path = Life360PathBuilder(context, CONFIG_ID).buildURLPath("ignored-domain")

        assertEquals("${Life360PathBuilder.NATIVO_ENDPOINT}&publisher_id=abc123", path)
    }

    private fun writeCustomParams(configId: String, vararg params: Pair<String, String>) {
        val editor = context.getSharedPreferences(Life360QueryParameterStore.prefsName(configId), Context.MODE_PRIVATE)
            .edit()
        params.forEach { (key, value) -> editor.putString(key, value) }
        editor.commit()
    }
}
