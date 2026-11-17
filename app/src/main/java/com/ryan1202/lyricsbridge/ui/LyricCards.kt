package com.ryan1202.lyricsbridge.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryan1202.lyricsbridge.BridgeUiState
import com.ryan1202.lyricsbridge.R

@Composable
internal fun LyricCard(
    state: BridgeUiState,
    modifier: Modifier = Modifier,
) {
    val frame = state.toLyricFrame()
    val lineStep = with(LocalDensity.current) { 48.dp.roundToPx() }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .heightIn(min = if (state.running) 220.dp else 144.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            LyricHeader(state)
            AnimatedLyrics(
                frame = frame,
                lineStep = lineStep,
                expanded = state.running
            )
            Text(
                text = if (state.running) {
                    state.status
                } else {
                    stringResource(R.string.start_sync_hint)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun LyricHeader(state: BridgeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lyrics),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = stringResource(R.string.current_lyric),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (state.guessed) {
            Text(
                text = stringResource(R.string.guessed_match),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        if (state.fetching) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun AnimatedLyrics(
    frame: LyricFrame,
    lineStep: Int,
    expanded: Boolean
) {
    AnimatedContent(
        targetState = frame,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (expanded) 132.dp else 64.dp)
            .clipToBounds(),
        transitionSpec = {
            val previousTimeMs = initialState.timeMs
            val currentTimeMs = targetState.timeMs
            val isSameSong = initialState.song == targetState.song
            val canScroll = isSameSong &&
                (previousTimeMs != null) &&
                (currentTimeMs != null)
            val direction = if (
                (previousTimeMs != null) &&
                (currentTimeMs != null) &&
                (currentTimeMs < previousTimeMs)
            ) {
                -1
            } else {
                1
            }
            val enter = if (canScroll) {
                slideInVertically(tween(220, easing = FastOutSlowInEasing)) {
                    direction * lineStep
                } + fadeIn(tween(150))
            } else {
                fadeIn(tween(150))
            }
            val exit = if (canScroll) {
                slideOutVertically(tween(220, easing = FastOutSlowInEasing)) {
                    -direction * lineStep
                } + fadeOut(tween(120))
            } else {
                fadeOut(tween(120))
            }
            (enter togetherWith exit).using(
                SizeTransform { _, _ -> snap() }
            )
        },
        label = "lyricScroll"
    ) { visibleFrame ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (visibleFrame.previous.isNotBlank()) {
                SecondaryLyricLine(visibleFrame.previous)
            }
            Text(
                text = visibleFrame.current,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (visibleFrame.next.isNotBlank()) {
                SecondaryLyricLine(visibleFrame.next)
            }
        }
    }
}

@Composable
private fun SecondaryLyricLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun BridgeUiState.toLyricFrame(): LyricFrame {
    val currentLyric = when {
        !running -> stringResource(R.string.waiting_first_lyric)
        song == null -> stringResource(R.string.waiting_music)
        fetching -> stringResource(R.string.finding_lyrics)
        else -> lyric.ifBlank { "···" }
    }
    return LyricFrame(
        song = song?.identity,
        timeMs = lyricTimeMs.takeIf { running && !fetching },
        previous = previousLyric,
        current = currentLyric,
        next = nextLyric
    )
}

private data class LyricFrame(
    val song: String?,
    val timeMs: Long?,
    val previous: String,
    val current: String,
    val next: String
)

@Composable
internal fun MatchCard(
    state: BridgeUiState,
    onDetails: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MatchStatus(state)
            if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                MatchSource(state)
            }
            if (state.match.isNotBlank()) {
                TextButton(
                    onClick = onDetails,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.match_details))
                }
            }
        }
    }
}

@Composable
private fun MatchStatus(state: BridgeUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val indicatorColor = when {
            state.error != null -> MaterialTheme.colorScheme.error
            state.running -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        }
        Box(
            Modifier
                .size(8.dp)
                .background(indicatorColor, CircleShape)
        )
        Text(
            text = if (state.running) state.status else stringResource(R.string.ready),
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun MatchSource(state: BridgeUiState) {
    val description = if (state.provider.isBlank()) {
        stringResource(R.string.match_hint)
    } else {
        stringResource(
            if (state.guessed) R.string.lyrics_from_guessed else R.string.lyrics_from,
            providerName(state.provider)
        )
    }
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun providerName(source: String): String = when (source) {
    "QQ音乐" -> stringResource(R.string.provider_qq)
    "网易云音乐" -> stringResource(R.string.provider_netease)
    else -> source
}
