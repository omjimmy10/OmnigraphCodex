package com.omnigraph.mobile.model

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

data class OmniGraphUiState(
    val mode: WorkMode = WorkMode.Encode,
    val busy: Boolean = false,
    val selectedAudioName: String? = null,
    val selectedImageName: String? = null,
    val status: String = "Ready",
    val encodedBitmap: Bitmap? = null,
    val decodedBitmap: Bitmap? = null,
    val sourcePlaybackFile: File? = null,
    val decodedPlaybackFile: File? = null,
    val canSaveImage: Boolean = false,
    val canSaveAudio: Boolean = false,
    val lastImageUri: Uri? = null,
    val lastAudioUri: Uri? = null,
    val error: String? = null,
)
