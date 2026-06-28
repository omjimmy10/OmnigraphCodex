package com.omnigraph.mobile.ui

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnigraph.mobile.util.toTimeText
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun PlaybackPanel(
    title: String,
    file: File?,
    displayName: String?,
    modifier: Modifier = Modifier,
) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }

    DisposableEffect(file) {
        playing = false
        positionMs = 0
        durationMs = 0

        val mediaPlayer = file?.takeIf { it.exists() }?.let {
            MediaPlayer().apply {
                setDataSource(it.absolutePath)
                prepare()
                durationMs = duration
                setOnCompletionListener { completed ->
                    playing = false
                    completed.seekTo(0)
                    positionMs = 0
                }
            }
        }
        player = mediaPlayer

        onDispose {
            mediaPlayer?.release()
            if (player === mediaPlayer) player = null
        }
    }

    LaunchedEffect(player, playing) {
        while (player != null) {
            if (playing) positionMs = player?.currentPosition ?: 0
            delay(180)
        }
    }

    fun seekBy(deltaMs: Int) {
        val activePlayer = player ?: return
        val target = (positionMs + deltaMs).coerceIn(0, durationMs.coerceAtLeast(0))
        activePlayer.seekTo(target)
        positionMs = target
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OmniDivider, RoundedCornerShape(10.dp))
            .background(OmniPanelSoft, RoundedCornerShape(10.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Headphones,
                    contentDescription = null,
                    tint = OmniBlue,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = OmniMuted,
                    )
                    Text(
                        text = displayName ?: "No audio selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OmniText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Waveform(positionMs = positionMs, durationMs = durationMs)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = positionMs.toTimeText(),
                    style = MaterialTheme.typography.labelMedium,
                    color = OmniGreen,
                )
                Slider(
                    modifier = Modifier.weight(1f),
                    enabled = player != null && durationMs > 0,
                    value = positionMs.coerceIn(0, durationMs.coerceAtLeast(1)).toFloat(),
                    valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                    onValueChange = { positionMs = it.toInt() },
                    onValueChangeFinished = { player?.seekTo(positionMs) },
                    colors = SliderDefaults.colors(
                        thumbColor = OmniGreen,
                        activeTrackColor = OmniGreen,
                        inactiveTrackColor = OmniDivider,
                    ),
                )
                Text(
                    text = durationMs.toTimeText(),
                    style = MaterialTheme.typography.labelMedium,
                    color = OmniMuted,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TransportButton(enabled = player != null, onClick = { seekBy(-10_000) }) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds")
                }
                TransportButton(enabled = player != null, onClick = { seekBy(-30_000) }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Back")
                }
                IconButton(
                    enabled = player != null,
                    modifier = Modifier
                        .size(64.dp)
                        .border(2.dp, OmniGreen, CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = OmniText,
                        disabledContentColor = OmniMuted,
                    ),
                    onClick = {
                        val activePlayer = player ?: return@IconButton
                        if (playing) {
                            activePlayer.pause()
                            playing = false
                        } else {
                            activePlayer.start()
                            playing = true
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(34.dp),
                    )
                }
                TransportButton(enabled = player != null, onClick = { seekBy(30_000) }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Forward")
                }
                TransportButton(enabled = player != null, onClick = { seekBy(10_000) }) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds")
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = OmniText,
            disabledContentColor = OmniMuted,
        ),
        onClick = onClick,
        content = content,
    )
}

@Composable
private fun Waveform(positionMs: Int, durationMs: Int) {
    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
    ) {
        val bars = 72
        val gap = size.width / bars
        val stroke = 2.dp.toPx()
        repeat(bars) { index ->
            val normalized = kotlin.math.abs(kotlin.math.sin(index * 0.34f)) * 0.75f + 0.18f
            val barHeight = size.height * normalized
            val x = gap * index + gap / 2f
            val active = index.toFloat() / bars.toFloat() <= fraction
            drawLine(
                color = if (active) OmniBlue else OmniDivider,
                start = Offset(x, size.height / 2f - barHeight / 2f),
                end = Offset(x, size.height / 2f + barHeight / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}



