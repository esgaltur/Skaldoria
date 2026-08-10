package com.skaldoria.core.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QrCodeGeneratorTest {

    @Test
    fun testEncodeShortUrl() {
        val url = "http://127.0.0.1:8888/remote"
        val qr = QrCodeGenerator.encode(url)

        assertNotNull(qr)
        assertTrue(qr.size >= 21, "QR code size should be at least 21x21")
        assertEquals(qr.size, qr.modules.size)
        assertEquals(qr.size, qr.modules[0].size)

        // Verify Top-Left Finder Pattern (7x7 with outer black border and 3x3 center)
        assertTrue(qr.isDark(0, 0), "Top-left module should be dark")
        assertTrue(qr.isDark(0, 6), "Top-left finder border should be dark")
        assertTrue(qr.isDark(6, 0), "Top-left finder border should be dark")
        assertTrue(qr.isDark(6, 6), "Top-left finder border should be dark")
        assertFalse(qr.isDark(1, 1), "Finder separator ring should be light")
        assertTrue(qr.isDark(3, 3), "Finder center module should be dark")

        // Verify Top-Right Finder Pattern
        val s = qr.size
        assertTrue(qr.isDark(0, s - 7))
        assertTrue(qr.isDark(0, s - 1))
        assertTrue(qr.isDark(6, s - 7))
        assertTrue(qr.isDark(6, s - 1))
        assertTrue(qr.isDark(3, s - 4))

        // Verify Bottom-Left Finder Pattern
        assertTrue(qr.isDark(s - 7, 0))
        assertTrue(qr.isDark(s - 1, 0))
        assertTrue(qr.isDark(s - 7, 6))
        assertTrue(qr.isDark(s - 1, 6))
        assertTrue(qr.isDark(s - 4, 3))

        // Verify Dark Module
        assertTrue(qr.isDark(s - 8, 8), "Standard dark module at (size-8, 8) must be dark")
    }

    @Test
    fun testEncodeAudienceUrl() {
        val audienceUrl = "http://192.168.1.150:8888/audience"
        val qr = QrCodeGenerator.encode(audienceUrl)

        assertNotNull(qr)
        assertTrue(qr.size >= 25, "Audience URL should map to Version 2 or 3 (size >= 25)")

        // Check horizontal and vertical timing patterns (row 6 and col 6 alternating between finder patterns)
        for (i in 8 until qr.size - 8) {
            val expected = (i % 2 == 0)
            assertEquals(expected, qr.isDark(6, i), "Horizontal timing pattern at (6, $i)")
            assertEquals(expected, qr.isDark(i, 6), "Vertical timing pattern at ($i, 6)")
        }
    }
}
