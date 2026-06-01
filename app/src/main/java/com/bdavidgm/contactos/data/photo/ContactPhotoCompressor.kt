package com.bdavidgm.contactos.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.bdavidgm.contactos.data.vcf.DEFAULT_MAX_INLINE_PHOTO_BYTES
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reduce una imagen elegida desde la galería a JPEG que no supere [maxBytes],
 * para que encaje en la exportación VCF con PHOTO en Base64.
 */
object ContactPhotoCompressor {

    private const val MAX_DECODE_EDGE_PX = 3072

    fun compressPickedPhotoToJpegMaxBytes(
        context: Context,
        uri: Uri,
        maxBytes: Long = DEFAULT_MAX_INLINE_PHOTO_BYTES,
    ): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_DECODE_EDGE_PX ||
            bounds.outHeight / sample > MAX_DECODE_EDGE_PX
        ) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, opts)
        } ?: return null

        return compressBitmapToJpegUnderMaxBytes(decoded, maxBytes)
    }

    private fun compressBitmapToJpegUnderMaxBytes(decoded: Bitmap, maxBytes: Long): ByteArray? {
        var current = decoded
        try {
            repeat(28) {
                var q = 92
                while (q >= 38) {
                    val bytes = jpegEncode(current, q) ?: break
                    if (bytes.size.toLong() <= maxBytes) return bytes
                    q -= 7
                }
                val nw = max(360, (current.width * 0.82f).roundToInt())
                val nh = max(360, (current.height * 0.82f).roundToInt())
                if (nw >= current.width - 2 && nh >= current.height - 2) {
                    return jpegEncode(current, 36)
                }
                val scaled = Bitmap.createScaledBitmap(current, nw, nh, true) ?: return null
                if (!current.isRecycled && current !== scaled) {
                    current.recycle()
                }
                current = scaled
            }
            return jpegEncode(current, 32)
        } finally {
            if (!current.isRecycled) current.recycle()
        }
    }

    private fun jpegEncode(bitmap: Bitmap, quality: Int): ByteArray? =
        runCatching {
            ByteArrayOutputStream().use { out ->
                val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
                if (!ok) return null
                out.toByteArray()
            }
        }.getOrNull()
}
