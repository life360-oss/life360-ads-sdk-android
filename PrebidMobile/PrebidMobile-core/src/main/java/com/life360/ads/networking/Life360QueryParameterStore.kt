package com.life360.ads.networking

import android.content.Context

/**
 * Reads developer-configured query parameters appended to Life360 bid requests, scoped per ad
 * unit by [configId]. Internal Life360 engineers write these directly as key/value entries in the
 * per-configId SharedPreferences file (see [prefsName]); 
 * 
 * Example usage:
 * context.getSharedPreferences(Life360QueryParameterStore.prefsName(configId), Context.MODE_PRIVATE)
    .edit()
    .putString("param_a", "394223")
    .apply()
 */
object Life360QueryParameterStore {
    private const val PREFS_PREFIX = "l360_exchange_params_"

    fun prefsName(configId: String): String = "$PREFS_PREFIX$configId"

    fun all(context: Context, configId: String): Map<String, String> {
        val prefs = context.getSharedPreferences(prefsName(configId), Context.MODE_PRIVATE)
        // Non-string entries are skipped rather than poisoning the whole map, since each entry is
        // its own independent SharedPreferences key rather than a single parsed blob.
        return prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
    }
}
