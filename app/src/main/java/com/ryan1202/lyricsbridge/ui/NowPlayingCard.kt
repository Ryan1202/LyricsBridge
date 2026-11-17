package com.ryan1202.lyricsbridge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryan1202.lyricsbridge.BridgeUiState
import com.ryan1202.lyricsbridge.R
import java.util.Locale

@Composable
internal fun NowPlayingCard(
    state: BridgeUiState,
    canPlayPause: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canSeek: Boolean,
    onSeek: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlaybackHeader(state)
            TrackSummary(state)
            PlaybackControls(
                state = state,
                canPlayPause = canPlayPause,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                canSeek = canSeek,
                onSeek = onSeek
            )
        }
    }
}

@Composable
private fun PlaybackHeader(state: BridgeUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "APPLE MUSIC",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                state.song == null -> stringResource(R.string.waiting_playback)
                state.playing -> stringResource(R.string.playing)
                else -> stringResource(R.string.paused)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TrackSummary(state: BridgeUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val artwork = state.artwork
            if (artwork != null) {
                Image(
                    bitmap = artwork.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_music),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = state.song?.title ?: stringResource(R.string.next_song_placeholder),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.song?.artist ?: stringResource(R.string.play_music_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            state.song?.album
                ?.takeIf(String::isNotBlank)
                ?.let { album ->
                    Text(
                        text = album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackControls(
    state: BridgeUiState,
    canPlayPause: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canSeek: Boolean,
    onSeek: (Long) -> Unit,
) {
    val durationMs = state.song?.durationMs ?: 0L
    var draggedProgress by remember(state.song?.identity) {
        mutableStateOf<Float?>(null)
    }
    val displayedProgress = draggedProgress ?: state.playbackProgress(durationMs)
    val seekEnabled = canSeek && (durationMs > 0)
    val playbackDescription = stringResource(R.string.playback_position)

    Column {
        Slider(
            value = displayedProgress,
            onValueChange = { draggedProgress = it },
            onValueChangeFinished = {
                draggedProgress?.let { progress ->
                    onSeek((progress * durationMs).toLong().coerceIn(0, durationMs))
                }
                draggedProgress = null
            },
            enabled = seekEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = playbackDescription },
            thumb = {
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(
                                color = if (seekEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                                shape = CircleShape
                            )
                    )
                }
            },
            track = { slider ->
                SliderDefaults.Track(
                    sliderState = slider,
                    enabled = seekEnabled,
                    modifier = Modifier.height(3.dp),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp
                )
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayedPositionMs = draggedProgress
                ?.let { (it * durationMs).toLong() }
                ?: state.positionMs
            TimeLabel(displayedPositionMs, Modifier.weight(1f), TextAlign.Start)
            IconButton(
                onClick = onPrevious,
                enabled = canSkipPrevious,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_previous),
                    contentDescription = stringResource(R.string.previous_track),
                    modifier = Modifier.size(24.dp)
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = canPlayPause,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (state.playing) R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = stringResource(
                        if (state.playing) R.string.pause else R.string.play
                    ),
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(
                onClick = onNext,
                enabled = canSkipNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_next),
                    contentDescription = stringResource(R.string.next_track),
                    modifier = Modifier.size(24.dp)
                )
            }
            TimeLabel(durationMs, Modifier.weight(1f), TextAlign.End)
        }
    }
}

@Composable
private fun TimeLabel(
    timeMs: Long,
    modifier: Modifier,
    alignment: TextAlign
) {
    Text(
        text = formatTime(timeMs),
        modifier = modifier,
        maxLines = 1,
        textAlign = alignment,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun BridgeUiState.playbackProgress(durationMs: Long): Float {
    if (durationMs <= 0) return 0f
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

private fun formatTime(timeMs: Long): String {
    val safeTimeMs = timeMs.coerceAtLeast(0)
    return String.format(
        Locale.ROOT,
        "%d:%02d",
        safeTimeMs / 60_000,
        (safeTimeMs / 1_000) % 60
    )
}
