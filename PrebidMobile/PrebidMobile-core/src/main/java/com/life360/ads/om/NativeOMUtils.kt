package com.life360.ads.om

import org.json.JSONArray
import org.json.JSONObject
import org.prebid.mobile.LogUtil
import java.net.URL

/**
 * Locates the Open Measurement verification resource in a native bid response.
 *
 * The IAB integration guide explicitly declines to standardise where OM resources live in a native
 * response — there is no standard native ad response format, so the resource arrives "most likely" as part
 * of the response by whatever mechanism the exchange picked. This searches the
 * known shapes in order of how specific each is, and stops at the first resource complete enough to measure
 * with. Callers pass the native markup and get a resource or nothing; which shapes exist and how their keys
 * are spelled stays in here.
 */
object NativeOMUtils {

    private const val TAG = "Life360NativeOMUtils"

    /** OpenRTB native `eventtrackers[].method` value for a JavaScript tracker. */
    private const val EVENT_TRACKING_METHOD_JS = 2

    /** OpenRTB native `eventtrackers[].event` value reserved for an OMID verification resource. */
    private const val EVENT_TYPE_OMID = 555

    private val URL_KEYS = listOf(
        "url",
        "omidJsUrl",
        "javascriptResourceUrl",
        "jsResourceUrl",
        "resourceUrl",
        "script",
    )

    private val VENDOR_KEYS = listOf("vendorKey", "vendor")

    private val PARAMETER_KEYS = listOf("verificationParameters", "verificationParams", "params")

    /**
     * Ordered list of places a verification resource is known to appear. The earlier phases are the more
     * explicit declarations, so they win over the looser VAST-style fallback.
     */
    private val searchPhases: List<Pair<String, (JSONObject) -> NativeOMResource?>> = listOf(
        "eventtrackers" to ::resourceFromEventTrackers,
        "native.ext.omid" to ::resourceFromOmidExt,
        "native.ext.adverifications" to ::resourceFromAdVerifications,
    )

    /**
     * Returns the first complete verification resource found in the markup, or null if the response carries
     * none — which is the common case, since only some demand includes OM measurement.
     *
     * @param nativeMarkup the `native` object of the bid's `adm`.
     */
    @JvmStatic
    fun verificationResource(nativeMarkup: JSONObject?): NativeOMResource? {
        if (nativeMarkup == null) {
            return null
        }

        for ((name, search) in searchPhases) {
            val resource = search(nativeMarkup)
            if (resource != null) {
                LogUtil.debug(TAG, "Found Open Measurement verification resource in $name — vendor ${resource.vendorKey}")
                return resource
            }
        }

        LogUtil.debug(TAG, "No Open Measurement verification resource found in the native ad markup")
        return null
    }

    /**
     * True when this event tracker carries an OMID verification resource, which must be handed to the OM SDK
     * rather than fetched as though it were an impression pixel.
     *
     * Deliberately narrower than "is a JavaScript tracker": `method: 2` on its own also describes an ordinary
     * JS impression tracker, and treating one of those as a verification resource would both lose the
     * impression and hand the OM SDK a script that reports nothing. The same predicate decides what the OM
     * SDK receives and what the pixel path skips, so the two can never disagree about a tracker.
     */
    @JvmStatic
    fun isOmidEventTracker(eventTracker: JSONObject?): Boolean {
        if (eventTracker == null || eventTracker.optInt("method", -1) != EVENT_TRACKING_METHOD_JS) {
            return false
        }
        if (eventTracker.optInt("event", -1) == EVENT_TYPE_OMID) {
            return true
        }

        // Some exchanges declare the resource against the generic impression event instead of 555. A vendor
        // key and verification parameters are what a verification resource has and an impression tracker
        // does not, so the shape settles it without reading the event type.
        val ext = eventTracker.optJSONObject("ext") ?: return false
        return string(ext, VENDOR_KEYS) != null && string(ext, PARAMETER_KEYS) != null
    }

    // region Phase 1: event trackers

    /**
     * The OpenRTB-native way to declare a verification resource: an `eventtrackers` entry with
     * `event: 555, method: 2`, whose `ext` carries the vendor key and verification parameters alongside the
     * script `url`.
     */
    private fun resourceFromEventTrackers(nativeMarkup: JSONObject): NativeOMResource? {
        val trackers = nativeMarkup.optJSONArray("eventtrackers") ?: return null

        val omidTrackers = ArrayList<JSONObject>()
        for (i in 0 until trackers.length()) {
            val tracker = trackers.optJSONObject(i) ?: continue
            if (isOmidEventTracker(tracker)) {
                omidTrackers.add(tracker)
            }
        }

        // An explicit event 555 outranks one merely recognised by its ext, so a response carrying both
        // resolves to the one the exchange actually labelled.
        val ordered = omidTrackers.sortedByDescending { it.optInt("event", -1) == EVENT_TYPE_OMID }

        for (tracker in ordered) {
            val ext = tracker.optJSONObject("ext") ?: continue

            // The script URL is normally the tracker's own `url`, but some responses only put it in `ext`.
            val url = tracker.optString("url").nonEmpty() ?: string(ext, URL_KEYS)

            val resource = makeResource(url, string(ext, VENDOR_KEYS), string(ext, PARAMETER_KEYS))
            if (resource != null) {
                return resource
            }
        }

        return null
    }

    // endregion

    // region Phase 2: native.ext.omid

    /** A dedicated `omid` object (or array of them) hung off the native markup's `ext`. */
    private fun resourceFromOmidExt(nativeMarkup: JSONObject): NativeOMResource? {
        val ext = nativeMarkup.optJSONObject("ext") ?: return null

        for (candidate in objects(ext, listOf("omid", "openmeasurement", "om"))) {
            val resource = resource(candidate)
            if (resource != null) {
                return resource
            }
        }

        return null
    }

    // endregion

    // region Phase 3: native.ext adverifications

    /**
     * The VAST `AdVerifications` shape, which bidders serving both video and native tend to reuse for native
     * rather than inventing a second format.
     */
    private fun resourceFromAdVerifications(nativeMarkup: JSONObject): NativeOMResource? {
        val ext = nativeMarkup.optJSONObject("ext") ?: return null

        for (container in objects(ext, listOf("adVerifications")) + listOf(ext)) {
            for (verification in objects(container, listOf("verifications", "verification"))) {
                val resource = resource(verification)
                if (resource != null) {
                    return resource
                }
            }
        }

        return null
    }

    // endregion

    // region Resource assembly

    /**
     * Reads a resource out of a single object, whatever phase handed it over — the key spellings are the same
     * wherever the object is nested.
     */
    private fun resource(json: JSONObject): NativeOMResource? {
        return makeResource(
            string(json, URL_KEYS),
            string(json, VENDOR_KEYS),
            string(json, PARAMETER_KEYS),
        )
    }

    /**
     * Builds a resource only when every field is present and the URL is usable, logging what was missing
     * otherwise — a resource the OM SDK silently drops is much harder to diagnose than a log line.
     */
    private fun makeResource(url: String?, vendorKey: String?, parameters: String?): NativeOMResource? {
        if (url == null || vendorKey == null || parameters == null) {
            if (url != null || vendorKey != null || parameters != null) {
                LogUtil.warning(
                    TAG,
                    "Incomplete Open Measurement verification resource in native markup. " +
                        "Url: $url, vendorKey: $vendorKey, params: $parameters",
                )
            }
            return null
        }

        // The scheme is checked rather than just parseability: the OM SDK fetches this script over the
        // network, so a relative reference or a javascript: URL is not something to hand it.
        val scheme = try {
            URL(url).protocol?.lowercase()
        } catch (throwable: Throwable) {
            null
        }
        if (scheme != "https" && scheme != "http") {
            LogUtil.warning(TAG, "Open Measurement verification resource has an unusable URL: $url")
            return null
        }

        return NativeOMResource(url, vendorKey, parameters)
    }

    // endregion

    // region Lenient JSON lookup

    /**
     * Looks up the first non-empty string among [keys], ignoring case and word separators so that
     * `vendorKey`, `vendorkey` and `vendor_key` all resolve to the same value. Bidders are inconsistent about
     * casing and this is cheaper than teaching every call site all three spellings.
     */
    private fun string(json: JSONObject, keys: List<String>): String? {
        val normalized = normalize(json)

        for (key in keys) {
            val value = normalized[normalizeKey(key)] ?: continue

            (value as? String)?.nonEmpty()?.let { return it }
            // Some exchanges wrap a single-element array around the value.
            if (value is JSONArray) {
                for (i in 0 until value.length()) {
                    (value.opt(i) as? String)?.nonEmpty()?.let { return it }
                }
            }
        }

        return null
    }

    /**
     * Returns the objects found under any of [keys], flattening the object-or-array ambiguity that shows up
     * in nearly every one of these shapes.
     */
    private fun objects(json: JSONObject, keys: List<String>): List<JSONObject> {
        val normalized = normalize(json)

        for (key in keys) {
            val value = normalized[normalizeKey(key)] ?: continue

            if (value is JSONObject) {
                return listOf(value)
            }
            if (value is JSONArray) {
                val result = ArrayList<JSONObject>(value.length())
                for (i in 0 until value.length()) {
                    value.optJSONObject(i)?.let(result::add)
                }
                if (result.isNotEmpty()) {
                    return result
                }
            }
        }

        return emptyList()
    }

    private fun normalize(json: JSONObject): Map<String, Any> {
        val result = HashMap<String, Any>(json.length())
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.opt(key)
            if (value != null && value != JSONObject.NULL) {
                result[normalizeKey(key)] = value
            }
        }
        return result
    }

    private fun normalizeKey(key: String): String {
        return key.lowercase().filter { it != '_' && it != '-' && it != ' ' }
    }

    /** Null rather than an empty string, so a present-but-blank JSON value is treated as absent. */
    private fun String.nonEmpty(): String? = if (isEmpty()) null else this

    // endregion
}
