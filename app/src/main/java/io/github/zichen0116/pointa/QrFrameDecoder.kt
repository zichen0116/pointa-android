package io.github.zichen0116.pointa

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

internal data class QrPoint(val x: Float, val y: Float)
internal data class QrDetection(val text: String, val corners: List<QrPoint>)

internal fun decodeQrFrame(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int,
    left: Int = 0, top: Int = 0, cropWidth: Int = width, cropHeight: Int = height,
    reader: MultiFormatReader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }): QrDetection? {
    require(width > 0 && height > 0 && pixelStride > 0 && rowStride.toLong() >= (width - 1L) * pixelStride + 1)
    require(left >= 0 && top >= 0 && cropWidth > 0 && cropHeight > 0 && left.toLong() + cropWidth <= width && top.toLong() + cropHeight <= height)
    require(cropWidth.toLong() * cropHeight <= 32_000_000)
    val offset = buffer.position()
    val last = offset.toLong() + (top + cropHeight - 1L) * rowStride + (left + cropWidth - 1L) * pixelStride
    require(last < buffer.limit())
    val data = ByteArray(cropWidth * cropHeight)
    for (row in 0 until cropHeight) for (column in 0 until cropWidth)
        data[row * cropWidth + column] = buffer.get(offset + (top + row) * rowStride + (left + column) * pixelStride)
    val source = PlanarYUVLuminanceSource(data, cropWidth, cropHeight, 0, 0, cropWidth, cropHeight, false)
    return try {
        val decoded = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        if (decoded.text.length > 2048) return null
        val points = decoded.resultPoints.take(3).map { QrPoint(it.x + left, it.y + top) }
        if (points.size != 3) return null
        // ZXing returns bottom-left, top-left, top-right finder centers; expand to frame the code.
        val fourth = QrPoint(points[0].x + points[2].x - points[1].x, points[0].y + points[2].y - points[1].y)
        val center = QrPoint((points[0].x + points[2].x) / 2, (points[0].y + points[2].y) / 2)
        QrDetection(decoded.text, (points + fourth).map { QrPoint(center.x + (it.x - center.x) * 1.22f, center.y + (it.y - center.y) * 1.22f) })
    }
    catch (_: ReaderException) { null }
    finally { reader.reset() }
}
