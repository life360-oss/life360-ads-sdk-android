package com.life360.ads.bid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the wire-format contract of [Life360AdType.fromInt]: the integer values are sent by the
 * server, so an accidental reordering or renumbering of the enum would silently misroute ad types.
 */
class Life360AdTypeTest {

    @Test
    fun fromInt_mapsEachKnownValue() {
        assertEquals(Life360AdType.ARTICLE, Life360AdType.fromInt(0))
        assertEquals(Life360AdType.DISPLAY, Life360AdType.fromInt(2))
        assertEquals(Life360AdType.CTP_VIDEO, Life360AdType.fromInt(3))
        assertEquals(Life360AdType.CAROUSEL, Life360AdType.fromInt(4))
        assertEquals(Life360AdType.STP_VIDEO, Life360AdType.fromInt(5))
        assertEquals(Life360AdType.STANDARD_DISPLAY, Life360AdType.fromInt(6))
        assertEquals(Life360AdType.STORY, Life360AdType.fromInt(7))
    }

    @Test
    fun fromInt_gapValueOne_returnsNull() {
        // 1 is intentionally absent from the enum.
        assertNull(Life360AdType.fromInt(1))
    }

    @Test
    fun fromInt_unknownValue_returnsNull() {
        assertNull(Life360AdType.fromInt(99))
        assertNull(Life360AdType.fromInt(-1))
    }
}
