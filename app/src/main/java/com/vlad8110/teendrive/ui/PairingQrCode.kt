package com.vlad8110.teendrive.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
fun PairingQrCode(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 236.dp,
) {
    val bitmap = remember(payload) { createQrBitmap(payload) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "TeenDrive pairing QR code",
            modifier = Modifier.size(size),
        )
    }
}

fun createQrBitmap(
    payload: String,
    sizePx: Int = 768,
): Bitmap {
    val pixels = createQrPixels(payload, sizePx)
    return createBitmap(sizePx, sizePx).apply {
        setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    }
}

fun createQrPixels(
    payload: String,
    sizePx: Int = 768,
): IntArray {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2,
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        for (x in 0 until sizePx) {
            pixels[y * sizePx + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return pixels
}
