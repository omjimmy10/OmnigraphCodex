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

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) encodeAudio(uri)
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) decodeImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniGraphApp(
                versionName = BuildConfig.VERSION_NAME,
                state = uiState,
                onModeChange = { mode -> uiState = uiState.copy(mode = mode) },
                onPickAudio = { pickAudio.launch(arrayOf("audio/*", "application/octet-stream")) },
                onPickImage = { pickImage.launch(arrayOf("image/png")) },
                onSaveImage = ::saveEncodedImage,
                onSaveAudio = ::saveDecodedAudio,
                onDecodeGeneratedImage = ::decodeGeneratedImage,
                onOpenImage = ::openLastImage,
                onOpenAudio = ::openLastAudio,
                onReset = ::resetCurrentMode,
                onClearError = { uiState = uiState.copy(error = null) },
            )
        }
    }

    private fun encodeAudio(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val name = displayName(uri)
                uiState = uiState.copy(
                    mode = WorkMode.Encode,
                    busy = true,
                    selectedAudioName = name,
                    status = "Normalizing audio...",
                    error = null,
                    encodedBitmap = null,
                    sourcePlaybackFile = null,
                    canSaveImage = false,
                )

                val normalized = withContext(Dispatchers.IO) {
                    AudioNormalizer.normalize(this@MainActivity, uri)
                }

                uiState = uiState.copy(status = "Encoding Method A PNG...")
                val rgbImage = withContext(Dispatchers.Default) { MethodACodec.encode(normalized.samples) }
                val bitmap = withContext(Dispatchers.Default) { BitmapPngCodec.toBitmap(rgbImage) }
                val sourceWav = withContext(Dispatchers.IO) { WavWriter.write(normalized.samples) }
                val playbackFile = withContext(Dispatchers.IO) { writeCacheFile("omnigraph_normalized.wav", sourceWav) }

                encodedBitmapForSave = bitmap
                uiState = uiState.copy(
                    busy = false,
                    status = "Encoded ${bitmap.width} x ${bitmap.height} PNG from ${normalized.samples.size} samples.",
                    encodedBitmap = bitmap,
                    sourcePlaybackFile = playbackFile,
                    canSaveImage = true,
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    busy = false,
                    status = "Ready",
                    error = error.message ?: "Audio encoding failed.",
                )
            }
        }
    }

    private fun decodeImage(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val name = displayName(uri)
                val mimeType = contentResolver.getType(uri)
                require(mimeType == "image/png" || name.endsWith(".png", ignoreCase = true)) {
                    "Method A images must be PNG. JPEG, screenshots, and WhatsApp images are lossy and will corrupt the decoded audio."
                }
                uiState = uiState.copy(
                    mode = WorkMode.Decode,
                    busy = true,
                    selectedImageName = name,
                    status = "Reading PNG...",
                    error = null,
                    decodedBitmap = null,
                    decodedPlaybackFile = null,
                    canSaveAudio = false,
                )

                val bitmap = withContext(Dispatchers.IO) { BitmapPngCodec.readBitmap(this@MainActivity, uri) }
                decodeBitmap(bitmap, name)
            }.onFailure { error ->
                uiState = uiState.copy(
                    busy = false,
                    status = "Ready",
                    error = error.message ?: "Image decoding failed.",
                )
            }
        }
    }

    private fun decodeGeneratedImage() {
        val bitmap = encodedBitmapForSave ?: return
        lifecycleScope.launch {
            runCatching {
                uiState = uiState.copy(
                    mode = WorkMode.Decode,
                    busy = true,
                    selectedImageName = "Generated image",
                    status = "Decoding generated PNG...",
                    error = null,
                )
                decodeBitmap(bitmap, "Generated image")
            }.onFailure { error ->
                uiState = uiState.copy(
                    busy = false,
                    status = "Ready",
                    error = error.message ?: "Generated image decoding failed.",
                )
            }
        }
    }

    private suspend fun decodeBitmap(bitmap: Bitmap, name: String) {
        uiState = uiState.copy(status = "Decoding Method A WAV...")
        val samples = withContext(Dispatchers.Default) {
            MethodACodec.decode(BitmapPngCodec.fromBitmap(bitmap))
        }
        val wavBytes = withContext(Dispatchers.IO) { WavWriter.write(samples) }
        val playbackFile = withContext(Dispatchers.IO) { writeCacheFile("omnigraph_decoded.wav", wavBytes) }

        decodedWavForSave = wavBytes
        uiState = uiState.copy(
            mode = WorkMode.Decode,
            busy = false,
            selectedImageName = name,
            status = "Decoded ${samples.size} samples to WAV.",
            decodedBitmap = bitmap,
            decodedPlaybackFile = playbackFile,
            canSaveAudio = true,
        )
    }

    private fun saveEncodedImage() {
        val bitmap = encodedBitmapForSave ?: return
        lifecycleScope.launch {
            runCatching {
                uiState = uiState.copy(busy = true, status = "Saving PNG...")
                val uri = withContext(Dispatchers.IO) {
                    MediaStoreSaver.savePng(this@MainActivity, bitmap, "omnigraph_${timestamp()}.png")
                }
                uiState = uiState.copy(
                    busy = false,
                    status = "Saved PNG to Pictures/OmniGraphMobile.",
                    lastImageUri = uri,
                )
            }.onFailure { error ->
                uiState = uiState.copy(busy = false, error = error.message ?: "Could not save PNG.")
            }
        }
    }

    private fun saveDecodedAudio() {
        val wavBytes = decodedWavForSave ?: return
        lifecycleScope.launch {
            runCatching {
                uiState = uiState.copy(busy = true, status = "Saving WAV...")
                val uri = withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveWav(this@MainActivity, wavBytes, "omnigraph_${timestamp()}.wav")
                }
                uiState = uiState.copy(
                    busy = false,
                    status = "Saved WAV to Music/OmniGraphMobile.",
                    lastAudioUri = uri,
                )
            }.onFailure { error ->
                uiState = uiState.copy(busy = false, error = error.message ?: "Could not save WAV.")
            }
        }
    }

    private fun openLastImage() {
        uiState.lastImageUri?.let { runCatching { openMedia(it, "image/png") } }
    }

    private fun openLastAudio() {
        uiState.lastAudioUri?.let { runCatching { openMedia(it, "audio/wav") } }
    }

    private fun resetCurrentMode() {
        val currentMode = uiState.mode
        uiState = if (currentMode == WorkMode.Encode) {
            encodedBitmapForSave = null
            uiState.copy(
                selectedAudioName = null,
                status = "Ready",
                encodedBitmap = null,
                sourcePlaybackFile = null,
                canSaveImage = false,
                error = null,
            )
        } else {
            decodedWavForSave = null
            uiState.copy(
                selectedImageName = null,
                status = "Ready",
                decodedBitmap = null,
                decodedPlaybackFile = null,
                canSaveAudio = false,
                error = null,
            )
        }
    }

    private fun writeCacheFile(name: String, bytes: ByteArray): File {
        val file = File(cacheDir, name)
        file.writeBytes(bytes)
        return file
    }

    private fun timestamp(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    }
}
