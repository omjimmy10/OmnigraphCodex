package com.omnigraph.mobile.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object MediaStoreSaver {
    fun savePng(context: Context, bitmap: Bitmap, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, ensureExtension(displayName, ".png"))
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniGraphMobile")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "Could not create a Gallery entry for the PNG."
        }

        try {
            resolver.openOutputStream(uri).use { output ->
                requireNotNull(output) { "Could not open Gallery output stream." }
                require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG compression failed." }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return uri
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    fun saveWav(context: Context, wavBytes: ByteArray, displayName: String): Uri {
        val resolver = context.contentResolver
        val fileName = ensureExtension(displayName, ".wav")
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(".wav"))
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/OmniGraphMobile")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val uri = requireNotNull(resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)) {
            "Could not create a Music entry for the WAV."
        }

        try {
            resolver.openOutputStream(uri).use { output ->
                requireNotNull(output) { "Could not open Music output stream." }
                output.write(wavBytes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return uri
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun ensureExtension(displayName: String, extension: String): String {
        return if (displayName.endsWith(extension, ignoreCase = true)) displayName else displayName + extension
    }
}