package com.omnigraph.mobile

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.omnigraph.mobile.audio.AudioNormalizer
import com.omnigraph.mobile.codec.BitmapPngCodec
import com.omnigraph.mobile.codec.MethodACodec
import com.omnigraph.mobile.codec.WavWriter
import com.omnigraph.mobile.model.OmniGraphUiState
import com.omnigraph.mobile.model.WorkMode
import com.omnigraph.mobile.storage.MediaStoreSaver
import com.omnigraph.mobile.ui.OmniGraphApp
import com.omnigraph.mobile.util.displayName
import com.omnigraph.mobile.util.openMedia
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var uiState by mutableStateOf(OmniGraphUiState())
    private var encodedBitmapForSave: Bitmap? = null
    private var decodedWavForSave: ByteArray? = null

    private val pickSource = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) encodeAudio(uri)
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) decodeImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clearPlaybackCache()
        setContent {
            OmniGraphApp(
                state = uiState,
                onPickAudio = { pickSource.launch(arrayOf("audio/*", "video/*", "application/octet-stream")) },
                onPickImage = { pickImage.launch(arrayOf("image/*")) },
                onSave = ::saveLatestOutput,
                onOpenImage = ::openLastImage,
                onOpenAudio = ::openLastAudio,
                onClearError = { uiState = uiState.copy(error = null) },
            )
        }
    }

    override fun onDestroy() {
        clearPlaybackCache()
        super.onDestroy()
    }

    private fun encodeAudio(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val name = displayName(uri)
                encodedBitmapForSave = null
                decodedWavForSave = null
                clearPlaybackCache()
                uiState = uiState.copy(
                    mode = WorkMode.Encode,
                    busy = true,
                    selectedAudioName = name,
                    selectedImageName = null,
                    status = "Encoding...",
                    error = null,
                    encodedBitmap = null,
                    decodedBitmap = null,
                    sourcePlaybackFile = null,
                    decodedPlaybackFile = null,
                    canSaveImage = false,
                    canSaveAudio = false,
                )

                val normalized = withContext(Dispatchers.IO) {
                    AudioNormalizer.normalize(this@MainActivity, uri)
                }

                val rgbImage = withContext(Dispatchers.Default) { MethodACodec.encode(normalized.samples) }
                val bitmap = withContext(Dispatchers.Default) { BitmapPngCodec.toBitmap(rgbImage) }
                val sourceWav = withContext(Dispatchers.IO) { WavWriter.write(normalized.samples) }
                val playbackFile = withContext(Dispatchers.IO) { writeCacheFile(NORMALIZED_CACHE_FILE, sourceWav) }

                encodedBitmapForSave = bitmap
                uiState = uiState.copy(
                    busy = false,
                    status = "Encoding completed successfully.",
                    encodedBitmap = bitmap,
                    sourcePlaybackFile = playbackFile,
                    canSaveImage = true,
                    canSaveAudio = false,
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    busy = false,
                    status = "",
                    error = error.message ?: "Audio encoding failed.",
                )
            }
        }
    }

    private fun decodeImage(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val name = displayName(uri)
                encodedBitmapForSave = null
                decodedWavForSave = null
                clearPlaybackCache()
                uiState = uiState.copy(
                    mode = WorkMode.Decode,
                    busy = true,
                    selectedAudioName = null,
                    selectedImageName = name,
                    status = "Decoding...",
                    error = null,
                    encodedBitmap = null,
                    decodedBitmap = null,
                    sourcePlaybackFile = null,
                    decodedPlaybackFile = null,
                    canSaveImage = false,
                    canSaveAudio = false,
                )

                val bitmap = withContext(Dispatchers.IO) { BitmapPngCodec.readBitmap(this@MainActivity, uri) }
                decodeBitmap(bitmap, name)
            }.onFailure { error ->
                uiState = uiState.copy(
                    busy = false,
                    status = "",
                    error = error.message ?: "Image decoding failed.",
                )
            }
        }
    }

    private suspend fun decodeBitmap(bitmap: Bitmap, name: String) {
        val samples = withContext(Dispatchers.Default) {
            MethodACodec.decode(BitmapPngCodec.fromBitmap(bitmap))
        }
        val wavBytes = withContext(Dispatchers.IO) { WavWriter.write(samples) }
        val playbackFile = withContext(Dispatchers.IO) { writeCacheFile(DECODED_CACHE_FILE, wavBytes) }

        decodedWavForSave = wavBytes
        uiState = uiState.copy(
            mode = WorkMode.Decode,
            busy = false,
            selectedImageName = name,
            status = "Decoding completed successfully.",
            decodedBitmap = bitmap,
            decodedPlaybackFile = playbackFile,
            canSaveImage = false,
            canSaveAudio = true,
        )
    }

    private fun saveLatestOutput() {
        if (uiState.busy) return
        if (uiState.mode == WorkMode.Decode && decodedWavForSave != null) {
            saveDecodedAudio()
        } else if (encodedBitmapForSave != null) {
            saveEncodedImage()
        }
    }

    private fun saveEncodedImage() {
        val bitmap = encodedBitmapForSave ?: return
        lifecycleScope.launch {
            runCatching {
                uiState = uiState.copy(busy = true, status = "Saving...")
                val uri = withContext(Dispatchers.IO) {
                    MediaStoreSaver.savePng(this@MainActivity, bitmap, "omnigraph_${timestamp()}.png")
                }
                uiState = uiState.copy(
                    busy = false,
                    status = "Saved successfully.",
                    lastImageUri = uri,
                )
            }.onFailure { error ->
                uiState = uiState.copy(busy = false, status = "", error = error.message ?: "Could not save PNG.")
            }
        }
    }

    private fun saveDecodedAudio() {
        val wavBytes = decodedWavForSave ?: return
        lifecycleScope.launch {
            runCatching {
                uiState = uiState.copy(busy = true, status = "Saving...")
                val uri = withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveWav(this@MainActivity, wavBytes, "omnigraph_${timestamp()}.wav")
                }
                uiState = uiState.copy(
                    busy = false,
                    status = "Saved successfully.",
                    lastAudioUri = uri,
                )
            }.onFailure { error ->
                uiState = uiState.copy(busy = false, status = "", error = error.message ?: "Could not save WAV.")
            }
        }
    }

    private fun openLastImage() {
        uiState.lastImageUri?.let { runCatching { openMedia(it, "image/png") } }
    }

    private fun openLastAudio() {
        uiState.lastAudioUri?.let { runCatching { openMedia(it, "audio/x-wav") } }
    }

    private fun writeCacheFile(name: String, bytes: ByteArray): File {
        val file = File(cacheDir, name)
        if (file.exists()) file.delete()
        file.writeBytes(bytes)
        return file
    }

    private fun clearPlaybackCache() {
        listOf(NORMALIZED_CACHE_FILE, DECODED_CACHE_FILE).forEach { fileName ->
            runCatching { File(cacheDir, fileName).delete() }
        }
    }

    private fun timestamp(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    }

    private companion object {
        const val NORMALIZED_CACHE_FILE = "omnigraph_normalized.wav"
        const val DECODED_CACHE_FILE = "omnigraph_decoded.wav"
    }
}