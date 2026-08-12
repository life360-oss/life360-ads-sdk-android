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
        fun fromInt(value: Int): Life360AdType? {
            return entries.firstOrNull { it.type == value }
        }
    }
}