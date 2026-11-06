package io.github.zichen0116.pointa

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class QrFrameDecoderTest {
    private val value = "pointa://pair?v=1&host=192.168.1.100&port=32145&id=" + "a".repeat(32) + "&pair=" + "b".repeat(32) + "&pin=" + "c".repeat(64) + "&token=" + "A".repeat(43)
    @Test fun realQrDecodesWithPaddingPositionPixelStrideAndRotation() {
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 400, 400)
        for (turn in 0..3) {
            val width = 420; val height = 420; val stride = width * 2 + 16; val prefix = 27
            val data = ByteBuffer.allocate(prefix + stride * height)
            for (i in 0 until data.capacity()) data.put(i, 255.toByte())
            for (y in 0 until 400) for (x in 0 until 400) {
                val (sx, sy) = when (turn) { 1 -> y to 399 - x; 2 -> 399 - x to 399 - y; 3 -> 399 - y to x; else -> x to y }
                data.put(prefix + (y + 10) * stride + (x + 10) * 2, if (matrix[sx, sy]) 0 else 255.toByte())
            }
            data.position(prefix)
            assertEquals(value, decodeQrFrame(data, width, height, stride, 2, 10, 10, 400, 400))
            assertEquals(prefix, data.position())
        }
    }
    @Test fun blankOrInvalidFramesAreHandledWithoutOutOfBoundsReads() {
        assertNull(decodeQrFrame(ByteBuffer.wrap(ByteArray(1600) { 255.toByte() }), 40, 40, 40, 1))
        assertThrows(IllegalArgumentException::class.java) { decodeQrFrame(ByteBuffer.allocate(10), 40, 40, 40, 1) }
        assertThrows(IllegalArgumentException::class.java) { decodeQrFrame(ByteBuffer.allocate(1600), 40, 40, 40, 1, left = 41) }
    }
}
