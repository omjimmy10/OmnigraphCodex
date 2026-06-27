package com.omnigraph.mobile.codec

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

object BitmapPngCodec {
    fun readBitmap(context: Context, uri: Uri): Bitmap {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open selected image." }
            val decoded = BitmapFactory.decodeStream(input)
            requireNotNull(decoded) { "Selected file is not a readable image." }
            return decoded.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    fun toBitmap(image: RgbImage): Bitmap {
        val colors = IntArray(image.width * image.height)
        for (index in colors.indices) {
            val offset = index * 3
            val red = image.pixels[offset].toInt() and 0xFF
            val green = image.pixels[offset + 1].toInt() and 0xFF
            val blue = image.pixels[offset + 2].toInt() and 0xFF
            colors[index] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return Bitmap.createBitmap(colors, image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    fun fromBitmap(bitmap: Bitmap): RgbImage {
        val normalized = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val width = normalized.width
        val height = normalized.height
        val colors = IntArray(width * height)
        normalized.getPixels(colors, 0, width, 0, 0, width, height)

        val pixels = ByteArray(colors.size * 3)
        for (index in colors.indices) {
            val color = colors[index]
            val offset = index * 3
            pixels[offset] = ((color shr 16) and 0xFF).toByte()
            pixels[offset + 1] = ((color shr 8) and 0xFF).toByte()
            pixels[offset + 2] = (color and 0xFF).toByte()
        }

        return RgbImage(width, height, pixels)
    }
}
