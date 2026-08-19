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
 * Pins the on-disk contract internal Life360 engineers rely on: individual string entries written
 * directly to the per-[Life360QueryParameterStore.prefsName] SharedPreferences file, scoped by
 * `configId` so different ad units don't share params.
 */
@RunWith(RobolectricTestRunner::class)
class Life360QueryParameterStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun all_readsValuesWrittenDirectlyToSharedPreferences() {
        prefs(CONFIG_ID).edit().putString("foo", "bar").putString("baz", "qux").commit()

        assertEquals(mapOf("foo" to "bar", "baz" to "qux"), Life360QueryParameterStore.all(context, CONFIG_ID))
    }

    @Test
    fun all_defaultsToEmptyWhenNoEntriesSet() {
        assertEquals(emptyMap<String, String>(), Life360QueryParameterStore.all(context, CONFIG_ID))
    }

    /**
     * A non-string entry is skipped rather than poisoning the whole map — worth pinning since each
     * entry is its own independent SharedPreferences key, not a single parsed blob.
     */
    @Test
    fun all_skipsNonStringEntriesButKeepsTheRest() {
        prefs(CONFIG_ID).edit().putString("foo", "bar").putInt("count", 123).commit()

        assertEquals(mapOf("foo" to "bar"), Life360QueryParameterStore.all(context, CONFIG_ID))
    }

    @Test
    fun all_isScopedPerConfigId_doesNotLeakBetweenConfigIds() {
        prefs(CONFIG_ID).edit().putString("foo", "bar").commit()
        prefs(OTHER_CONFIG_ID).edit().putString("foo", "different").commit()

        assertEquals(mapOf("foo" to "bar"), Life360QueryParameterStore.all(context, CONFIG_ID))
        assertEquals(mapOf("foo" to "different"), Life360QueryParameterStore.all(context, OTHER_CONFIG_ID))
    }

    private fun prefs(configId: String) =
        context.getSharedPreferences(Life360QueryParameterStore.prefsName(configId), Context.MODE_PRIVATE)
}
