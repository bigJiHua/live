package com.live.finance.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class PickedImage(val bytes: ByteArray, val fileName: String, val mime: String)

/**
 * 读取相册 Uri → 长边降采样到 maxSide(px) → 编码为 JPEG(quality)。替代 web 的 browser-image-compression。
 * 在 IO 线程调用。
 */
fun compressUriToJpeg(context: Context, uri: Uri, maxSide: Int = 1600, quality: Int = 82): PickedImage? {
    return try {
        val resolver = context.contentResolver
        // 1. 读尺寸
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val srcMax = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (srcMax / sample > maxSide) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp: Bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bmp.recycle()
        PickedImage(out.toByteArray(), "img_${System.currentTimeMillis()}.jpg", "image/jpeg")
    } catch (e: Exception) {
        null
    }
}
