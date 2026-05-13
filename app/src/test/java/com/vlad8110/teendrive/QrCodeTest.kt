package com.vlad8110.teendrive

import android.graphics.Color
import com.vlad8110.teendrive.ui.createQrPixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {
    @Test
    fun createsQrPixelBuffer() {
        val pixels = createQrPixels("teendrive://pair?code=123456", sizePx = 128)

        assertEquals(128 * 128, pixels.size)
        assertTrue(pixels.any { it == Color.BLACK })
        assertTrue(pixels.any { it == Color.WHITE })
    }
}
