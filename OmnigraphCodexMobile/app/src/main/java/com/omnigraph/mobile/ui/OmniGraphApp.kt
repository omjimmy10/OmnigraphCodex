package com.omnigraph.mobile.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnigraph.mobile.model.OmniGraphUiState
import com.omnigraph.mobile.model.WorkMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniGraphApp(
    versionName: String,
    state: OmniGraphUiState,
    onModeChange: (WorkMode) -> Unit,
    onPickAudio: () -> Unit,
    onPickImage: () -> Unit,
    onSaveImage: () -> Unit,
    onSaveAudio: () -> Unit,
    onDecodeGeneratedImage: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenAudio: () -> Unit,
    onReset: () -> Unit,
    onClearError: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    OmniGraphTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawer(
                    versionName = versionName,
                    onOpenImage = onOpenImage,
                    onOpenAudio = onOpenAudio,
                )
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "OmniGraph Mobile",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(text = versionName, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { padding ->
                OmniGraphOnePage(
                    state = state,
                    onPickAudio = onPickAudio,
                    onPickImage = onPickImage,
                    onSaveImage = onSaveImage,
                    onSaveAudio = onSaveAudio,
                    onDecodeGeneratedImage = onDecodeGeneratedImage,
                    onReset = onReset,
                    modifier = Modifier.padding(padding),
                )
            }
        }

        if (state.error != null) {
            AlertDialog(
                onDismissRequest = onClearError,
                confirmButton = { TextButton(onClick = onClearError) { Text("OK") } },
                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                title = { Text("Could not finish that step") },
                text = { Text(state.error) },
            )
        }
    }
}

@Composable
private fun OmniGraphOnePage(
    state: OmniGraphUiState,
    onPickAudio: () -> Unit,
    onPickImage: () -> Unit,
    onSaveImage: () -> Unit,
    onSaveAudio: () -> Unit,
    onDecodeGeneratedImage: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewBitmap = state.encodedBitmap ?: state.decodedBitmap
    val playbackFile = state.decodedPlaybackFile ?: state.sourcePlaybackFile
    val playbackTitle = if (state.decodedPlaybackFile != null) "Decoded Audio" else "Normalized Audio"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusLine(state = state)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !state.busy, onClick = onPickAudio) {
                    Icon(Icons.Filled.Audiotrack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pick Audio")
                }
                Button(enabled = !state.busy, onClick = onPickImage) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pick PNG")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = state.canSaveImage && !state.busy, onClick = onSaveImage) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save PNG")
                }
                OutlinedButton(enabled = state.canSaveAudio && !state.busy, onClick = onSaveAudio) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save WAV")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = state.encodedBitmap != null && !state.busy, onClick = onDecodeGeneratedImage) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Test Decode")
                }
                OutlinedButton(enabled = !state.busy, onClick = onReset) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset")
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FileNameLine("Audio", state.selectedAudioName)
            FileNameLine("Image", state.selectedImageName)
        }

        ImagePreview(bitmap = previewBitmap)
        PlaybackPanel(title = playbackTitle, file = playbackFile)
    }
}

@Composable
private fun AppDrawer(
    versionName: String,
    onOpenImage: () -> Unit,
    onOpenAudio: () -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "OmniGraph Mobile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = versionName, style = MaterialTheme.typography.labelMedium)
        }
        NavigationDrawerItem(
            label = { Text("Open last PNG") },
            selected = false,
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            onClick = onOpenImage,
        )
        NavigationDrawerItem(
            label = { Text("Open last WAV") },
            selected = false,
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            onClick = onOpenAudio,
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Output folders", style = MaterialTheme.typography.titleSmall)
            Text(text = "Pictures/OmniGraphMobile", style = MaterialTheme.typography.bodySmall)
            Text(text = "Music/OmniGraphMobile", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusLine(state: OmniGraphUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
            }
            Text(
                text = state.status,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FileNameLine(label: String, value: String?) {
    Text(
        text = "$label: ${value ?: "-"}",
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ImagePreview(bitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .aspectRatio(1f)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Image preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}