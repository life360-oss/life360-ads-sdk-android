package com.life360.ads.bid

enum class Life360AdType(val type: Int) {
    ARTICLE(0),
    DISPLAY(2),
    CTP_VIDEO(3),
    CAROUSEL(4),
    STP_VIDEO(5),
    STANDARD_DISPLAY(6),
    STORY(7);

    companion object {
        // entries requires the Kotlin 1.9+ stdlib; this module targets 1.8 for Geoedge AppHarbr compatibility.
        fun fromInt(value: Int): Life360AdType? {
            return values().firstOrNull { it.type == value }
        }
    }
}