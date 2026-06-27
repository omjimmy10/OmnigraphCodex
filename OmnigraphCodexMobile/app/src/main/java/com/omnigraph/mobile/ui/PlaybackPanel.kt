package com.omnigraph.mobile.ui

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import com.omnigraph.mobile.util.toTimeText
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun PlaybackPanel(
    title: String,
    file: File?,
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

        val mediaPlayer = file?.let {
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
            delay(250)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                enabled = player != null,
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
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(text = positionMs.toTimeText(), style = MaterialTheme.typography.labelMedium)
            Slider(
                modifier = Modifier.weight(1f),
                enabled = player != null && durationMs > 0,
                value = positionMs.coerceIn(0, durationMs.coerceAtLeast(1)).toFloat(),
                valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                onValueChange = { positionMs = it.toInt() },
                onValueChangeFinished = { player?.seekTo(positionMs) },
            )
            Text(text = durationMs.toTimeText(), style = MaterialTheme.typography.labelMedium)
        }
    }
}
