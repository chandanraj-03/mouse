package com.example.phonemouse

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MouseReportTest {
    @Test fun buildsFourByteReport() {
        assertArrayEquals(byteArrayOf(1, 5, -3, 0), MouseReport.build(MouseReport.LEFT, 5, -3, 0))
    }
    @Test fun clampsToSignedByteRange() {
        val r = MouseReport.build(0, 500, -500, 1000)
        assertArrayEquals(byteArrayOf(0, 127, -127, 127), r)
    }
    @Test fun masksButtons() {
        assertEquals(0x07, MouseReport.build(0xFF, 0, 0, 0)[0].toInt())
    }
    @Test fun splitMovePreservesTotal() {
        val parts = MouseReport.splitMove(300, -260)
        assertEquals(300, parts.sumOf { it.first })
        assertEquals(-260, parts.sumOf { it.second })
        assert(parts.all { it.first in -127..127 && it.second in -127..127 })
    }
    @Test fun descriptorLooksValid() {
        val d = MouseReport.DESCRIPTOR
        assertEquals(0x05, d[0].toInt()); assertEquals(0xC0.toByte(), d.last())
        assertEquals(52, d.size)
    }
}
