package io.github.zichen0116.pointa

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

internal fun decodeQrFrame(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int,
    left: Int = 0, top: Int = 0, cropWidth: Int = width, cropHeight: Int = height,
    reader: MultiFormatReader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }): String? {
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
    return try { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text.takeIf { it.length <= 2048 } }
    catch (_: ReaderException) { null }
    finally { reader.reset() }
}
