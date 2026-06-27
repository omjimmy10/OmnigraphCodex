package com.omnigraph.mobile.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.omnigraph.mobile.R
import com.omnigraph.mobile.model.OmniGraphUiState
import com.omnigraph.mobile.model.WorkMode
import kotlinx.coroutines.launch

private const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/omchari"
private const val REDDIT_URL = "https://www.reddit.com/r/OmnigraphCodex/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniGraphApp(
    versionName: String,
    state: OmniGraphUiState,
    onPickAudio: () -> Unit,
    onPickImage: () -> Unit,
    onSave: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenAudio: () -> Unit,
    onClearError: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showReadme by remember { mutableStateOf(false) }

    OmniGraphTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawer(
                    versionName = versionName,
                    onOpenImage = onOpenImage,
                    onOpenAudio = onOpenAudio,
                    onReadme = {
                        showReadme = true
                        scope.launch { drawerState.close() }
                    },
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
                    onSave = onSave,
                    modifier = Modifier.padding(padding),
                )
            }
        }

        if (showReadme) {
            ReadmeDialog(onDismiss = { showReadme = false })
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
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewBitmap = if (state.mode == WorkMode.Decode) state.decodedBitmap else state.encodedBitmap
    val playbackFile = state.decodedPlaybackFile ?: state.sourcePlaybackFile
    val playbackTitle = if (state.mode == WorkMode.Decode) "Decoded Audio" else "Normalized Audio"
    val canSave = (state.canSaveImage || state.canSaveAudio) && !state.busy

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MethodSelector()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !state.busy, onClick = onPickAudio, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Audiotrack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Encode Audio")
            }
            Button(enabled = !state.busy, onClick = onPickImage, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Decode Image")
            }
        }

        Button(enabled = canSave, onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("SAVE")
        }

        OperationMessage(state = state)
        ImagePreview(bitmap = previewBitmap)
        PlaybackPanel(title = playbackTitle, file = playbackFile)
    }
}

@Composable
private fun MethodSelector() {
    val uriHandler = LocalUriHandler.current
    var lockedTapCount by remember { mutableIntStateOf(0) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        MethodButton(label = "A", selected = true, locked = false, onClick = {})
        MethodButton(
            label = "B",
            selected = false,
            locked = true,
            onClick = {
                lockedTapCount += 1
                if (lockedTapCount >= 3) {
                    lockedTapCount = 0
                    uriHandler.openUri(BUY_ME_A_COFFEE_URL)
                }
            },
        )
        MethodButton(
            label = "C",
            selected = false,
            locked = true,
            onClick = {
                lockedTapCount += 1
                if (lockedTapCount >= 3) {
                    lockedTapCount = 0
                    uriHandler.openUri(BUY_ME_A_COFFEE_URL)
                }
            },
        )
    }
}

@Composable
private fun MethodButton(
    label: String,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        locked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        locked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun OperationMessage(state: OmniGraphUiState) {
    if (state.status.isBlank() && !state.busy) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Text(
            text = state.status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun AppDrawer(
    versionName: String,
    onOpenImage: () -> Unit,
    onOpenAudio: () -> Unit,
    onReadme: () -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                painter = painterResource(id = R.drawable.logo_omnigraph),
                contentDescription = "OmniGraph logo",
                modifier = Modifier.size(72.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = "OmniGraph Mobile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = versionName, style = MaterialTheme.typography.labelMedium)
        }
        NavigationDrawerItem(
            label = { Text("README") },
            selected = false,
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            onClick = onReadme,
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(text = "Encoded Images", style = MaterialTheme.typography.titleSmall)
            Text(text = "Pictures/OmniGraphMobile", style = MaterialTheme.typography.bodySmall)
        }
        NavigationDrawerItem(
            label = { Text("Open last image") },
            selected = false,
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            onClick = onOpenImage,
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(text = "Decoded Audio", style = MaterialTheme.typography.titleSmall)
            Text(text = "Music/OmniGraphMobile", style = MaterialTheme.typography.bodySmall)
        }
        NavigationDrawerItem(
            label = { Text("Open last audio") },
            selected = false,
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            onClick = onOpenAudio,
        )
    }
}

@Composable
private fun ReadmeDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(2.dp)),
        ) {
            Image(
                painter = painterResource(id = R.drawable.text_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Omnigraph Codex",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Converts audio data into pixel values and vice-versa to generate unique images and reconstruct sound.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "🔴 Method A – Stores different parts of the audio in Red, Green, and Blue channels sequentially.\n🔵 Method B – Interweaves audio data into all channels in each pixel simultaneously.\n🟢 Method C – Uses frequency spectrum analysis to distribute the audio across the image.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "🤔 Why I Created This",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Omnigraph Codex was designed for:\n✅ Creating visually generative, abstract, and glitch art\n✅ Sampling images into abstract audio pieces for music producers\n✅ Exploring new forms of audiovisual transformation",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "✨ Tip: Different methods produce distinct visual and audio patterns!\nExperiment to discover new textures and sounds.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "🌎 Join the Community!",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "I want this codex to bring together a community of artists and technical creators.\nIf you have any interesting creations, please share them at:",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "📌 r/OmnigraphCodex (Reddit)",
                    color = Color(0xFF7FB4FF),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { uriHandler.openUri(REDDIT_URL) },
                )
                Text(
                    text = "Let's build something amazing together! 🥺🎶🚀",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "📌 Developed by Om Chari and GPT",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("OK", textAlign = TextAlign.Center)
                }
            }
        }
    }
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