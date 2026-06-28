package com.omnigraph.mobile.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.omnigraph.mobile.R
import com.omnigraph.mobile.model.OmniGraphUiState
import com.omnigraph.mobile.model.WorkMode
import kotlinx.coroutines.launch

private const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/omchari"
private const val REDDIT_URL = "https://www.reddit.com/r/OmnigraphCodex/"
private const val PC_REPO_URL = "https://github.com/omjimmy10/OmnigraphCodex"
private const val COMPATIBILITY_TEXT = "Cross-platform compatible ✓; however, results may vary slightly between mobile and PC."
private const val LOCKED_METHOD_TEXT = "Methods B and C are not available on mobile yet."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniGraphApp(
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
    var showReadme by remember { mutableStateOf(false) }

    OmniGraphTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = 0.68f),
            drawerContent = {
                AppDrawer(
                    onClose = { scope.launch { drawerState.close() } },
                    onReadme = {
                        showReadme = true
                        scope.launch { drawerState.close() }
                    },
                )
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OmniBackground),
            ) {
                GlitchBackdrop(modifier = Modifier.fillMaxSize())
                OmniGraphOnePage(
                    state = state,
                    onMenu = { scope.launch { drawerState.open() } },
                    onPickAudio = onPickAudio,
                    onPickImage = onPickImage,
                    onSave = onSave,
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
                containerColor = OmniPanel,
                titleContentColor = OmniText,
                textContentColor = OmniText,
                iconContentColor = OmniBlue,
            )
        }
    }
}

@Composable
private fun OmniGraphOnePage(
    state: OmniGraphUiState,
    onMenu: () -> Unit,
    onPickAudio: () -> Unit,
    onPickImage: () -> Unit,
    onSave: () -> Unit,
) {
    val previewBitmap = if (state.mode == WorkMode.Decode) state.decodedBitmap else state.encodedBitmap
    val playbackFile = state.decodedPlaybackFile ?: state.sourcePlaybackFile
    val playbackTitle = if (state.mode == WorkMode.Decode) "Decoded audio" else "Playback"
    val playbackName = remember(state.mode, state.selectedAudioName, state.selectedImageName) {
        if (state.mode == WorkMode.Decode) {
            state.selectedImageName?.substringBeforeLast('.')?.let { "decoded_from_$it.wav" }
        } else {
            state.selectedAudioName
        }
    }
    val canSave = (state.canSaveImage || state.canSaveAudio) && !state.busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LogoHeader(onMenu = onMenu)
        MethodSelector()
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ActionButton(
                text = "ENCODE AUDIO",
                icon = Icons.Filled.Audiotrack,
                enabled = !state.busy,
                onClick = onPickAudio,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                text = "DECODE IMAGE",
                icon = Icons.Filled.Image,
                enabled = !state.busy,
                onClick = onPickImage,
                modifier = Modifier.weight(1f),
            )
        }
        ImagePreview(bitmap = previewBitmap)
        PlaybackPanel(
            title = playbackTitle,
            file = playbackFile,
            displayName = playbackName,
        )
        SaveButton(enabled = canSave, onClick = onSave)
        OperationMessage(state = state)
    }
}

@Composable
private fun LogoHeader(onMenu: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onMenu,
            colors = IconButtonDefaults.iconButtonColors(contentColor = OmniGreen),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu", modifier = Modifier.size(34.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .background(OmniGreen.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                        .border(1.dp, OmniGreen, RoundedCornerShape(16.dp)),
                )
                Image(
                    painter = painterResource(id = R.drawable.logo_omnigraph),
                    contentDescription = "OmnigraphCodex logo",
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = buildAnnotatedString {
                    append("OmnigraphCodex ")
                    withStyle(SpanStyle(color = OmniGreen)) { append("Mobile") }
                },
                style = MaterialTheme.typography.headlineSmall,
                color = OmniText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = COMPATIBILITY_TEXT,
                style = MaterialTheme.typography.bodySmall,
                color = OmniGreen.copy(alpha = 0.74f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }
}
@Composable
private fun MethodSelector() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var lockedTapCount by remember { mutableIntStateOf(0) }

    fun handleLockedMethodTap() {
        Toast.makeText(context, LOCKED_METHOD_TEXT, Toast.LENGTH_SHORT).show()
        lockedTapCount += 1
        if (lockedTapCount >= 3) {
            lockedTapCount = 0
            uriHandler.openUri(BUY_ME_A_COFFEE_URL)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OmniDivider, RoundedCornerShape(8.dp))
            .background(OmniPanel.copy(alpha = 0.84f), RoundedCornerShape(8.dp)),
    ) {
        MethodButton(letter = "A", selected = true, locked = false, onClick = {})
        MethodButton(letter = "B", selected = false, locked = true, onClick = ::handleLockedMethodTap)
        MethodButton(letter = "C", selected = false, locked = true, onClick = ::handleLockedMethodTap)
    }
}

@Composable
private fun RowScope.MethodButton(
    letter: String,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val foreground = when {
        selected -> OmniGreen
        locked -> OmniMuted.copy(alpha = 0.72f)
        else -> OmniText
    }

    Row(
        modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .background(if (selected) OmniGreen.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) OmniGreen else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (locked) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "METHOD",
                color = foreground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = letter,
                color = foreground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(76.dp)
            .alpha(if (enabled) 1f else 0.52f)
            .border(1.dp, OmniBlue, RoundedCornerShape(8.dp))
            .background(OmniBlue.copy(alpha = 0.09f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = OmniBlue, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = OmniBlue,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (enabled) OmniGreen else OmniDivider),
        colors = ButtonDefaults.buttonColors(
            containerColor = OmniGreen,
            contentColor = Color(0xFF00150A),
            disabledContainerColor = OmniPanelMuted,
            disabledContentColor = OmniMuted,
        ),
    ) {
        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text("SAVE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun OperationMessage(state: OmniGraphUiState) {
    if (state.status.isBlank() && !state.busy) return

    val isSuccess = state.status.contains("success", ignoreCase = true) ||
        state.status.contains("completed", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when {
            state.busy -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = OmniGreen,
            )
            isSuccess -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = OmniGreen,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = state.status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSuccess) OmniGreen else OmniText,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppDrawer(
    onClose: () -> Unit,
    onReadme: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    ModalDrawerSheet(
        drawerContainerColor = OmniPanel,
        drawerContentColor = OmniText,
        modifier = Modifier.width(322.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose, colors = IconButtonDefaults.iconButtonColors(contentColor = OmniGreen)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            Image(
                painter = painterResource(id = R.drawable.logo_omnigraph),
                contentDescription = "OmnigraphCodex logo",
                modifier = Modifier
                    .size(70.dp)
                    .border(1.dp, OmniGreen, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = buildAnnotatedString {
                    append("OmnigraphCodex\n")
                    withStyle(SpanStyle(color = OmniGreen)) { append("Mobile") }
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = OmniText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = COMPATIBILITY_TEXT,
                style = MaterialTheme.typography.bodySmall,
                color = OmniGreen.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(24.dp))
            DrawerButton(label = "README", icon = Icons.Filled.Info, tint = OmniMuted, onClick = onReadme)
            Spacer(Modifier.height(24.dp))
            DrawerSection(title = "ENCODED IMAGES", icon = Icons.Filled.Image, tint = OmniGreen)
            Text("Pictures/OmnigraphCodexMobile", color = OmniMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            Divider(color = OmniDivider)
            Spacer(Modifier.height(20.dp))
            DrawerSection(title = "DECODED AUDIO", icon = Icons.Filled.Audiotrack, tint = OmniBlue)
            Text("Music/OmnigraphCodexMobile", color = OmniMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Divider(color = OmniDivider)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "OmnigraphCodex for PC",
                color = OmniMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { uriHandler.openUri(PC_REPO_URL) },
            )
        }
    }
}

@Composable
private fun DrawerButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OmniDivider, RoundedCornerShape(8.dp))
            .background(OmniPanelSoft, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = tint, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DrawerSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(title, color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
@Composable
private fun ReadmeDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(4.dp)),
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
                    text = "🔴 Method A - Stores different parts of the audio in Red, Green, and Blue channels sequentially.\n🔵 Method B - Interweaves audio data into all channels in each pixel simultaneously.\n🟢 Method C - Uses frequency spectrum analysis to distribute the audio across the image.",
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
                    border = BorderStroke(1.dp, OmniBlue),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
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
            .heightIn(max = 380.dp)
            .aspectRatio(1f)
            .clipToBounds()
            .border(1.dp, OmniGreen, RoundedCornerShape(8.dp))
            .background(OmniPanelSoft, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            PreviewPlaceholder(modifier = Modifier.fillMaxSize())
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = OmniMuted,
                modifier = Modifier.size(42.dp),
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Image preview",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun PreviewPlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val lineCount = 72
        repeat(lineCount) { index ->
            val y = size.height * index / lineCount
            val wave = kotlin.math.sin(index * 0.52f) * 18f
            val color = when (index % 3) {
                0 -> OmniGreen.copy(alpha = 0.20f)
                1 -> OmniBlue.copy(alpha = 0.18f)
                else -> OmniMuted.copy(alpha = 0.12f)
            }
            drawLine(
                color = color,
                start = Offset(0f, y + wave),
                end = Offset(size.width, y - wave),
                strokeWidth = 1.3f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun GlitchBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val columns = 34
        repeat(columns) { column ->
            val x = size.width * column / columns
            val height = size.height * (0.08f + kotlin.math.abs(kotlin.math.sin(column * 0.61f)) * 0.22f)
            val top = size.height * 0.08f
            var y = top
            while (y < top + height) {
                drawLine(
                    color = OmniGreen.copy(alpha = 0.12f),
                    start = Offset(x, y),
                    end = Offset(x, y + 2.5f),
                    strokeWidth = 1.2f,
                )
                y += 9f
            }
        }
    }
}

