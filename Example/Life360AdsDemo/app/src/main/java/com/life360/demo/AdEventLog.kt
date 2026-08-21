package com.life360.demo

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf

/**
 * Keeps the SDK's callback sequence visible on screen. Open Measurement and tracking regressions
 * rarely look like a broken ad — they look like an impression that never fires, or a video that
 * doesn't pause when it leaves the viewport — so the callback order is the thing under test, and
 * reading it off logcat while scrolling is impractical on a device.
 *
 * Timestamps are relative to the slot's most recent load so the gap between "became visible" and
 * "impression fired" can be read directly.
 */
@Stable
class AdEventLog(private val capacity: Int = CAPACITY) {

    data class Entry(val elapsedMs: Long, val name: String, val detail: String?)

    private val entries = mutableStateListOf<Entry>()
    private var startedAtMs = SystemClock.elapsedRealtime()

    /** Newest last, so the sequence reads top to bottom. */
    val recent: List<Entry> get() = entries

    fun record(name: String, detail: String? = null) {
        if (entries.size == capacity) entries.removeAt(0)
        entries.add(Entry(SystemClock.elapsedRealtime() - startedAtMs, name, detail))
    }

    fun reset() {
        entries.clear()
        startedAtMs = SystemClock.elapsedRealtime()
    }

    private companion object {
        const val CAPACITY = 40
    }
}
