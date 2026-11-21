package com.ryan1202.lyricsbridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ryan1202.lyricsbridge.BridgeUiState
import com.ryan1202.lyricsbridge.R

internal data class PlaybackActions(
    val canPlayPause: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val canSeek: Boolean,
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BridgeTopBar(onOpenSettings: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_brand),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 24.dp, end = 12.dp)
                    .size(26.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings)
                )
            }
        }
    )
}

@Composable
internal fun BridgeBottomBar(
    state: BridgeUiState,
    accessGranted: Boolean,
    onToggleSync: () -> Unit,
    onOpenMusic: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onToggleSync,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.running) R.drawable.ic_stop else R.drawable.ic_play
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = syncButtonLabel(state.running, accessGranted),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                SecondaryActions(
                    state = state,
                    accessGranted = accessGranted,
                    onOpenMusic = onOpenMusic,
                    onRetry = onRetry
                )
            }
        }
    }
}

@Composable
private fun syncButtonLabel(running: Boolean, accessGranted: Boolean): String {
    return when {
        running -> stringResource(R.string.pause_sync)
        !accessGranted -> stringResource(R.string.connect_music)
        else -> stringResource(R.string.start_sync)
    }
}

@Composable
private fun SecondaryActions(
    state: BridgeUiState,
    accessGranted: Boolean,
    onOpenMusic: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = onOpenMusic,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.open_music))
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(R.drawable.ic_open),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        TextButton(
            onClick = onRetry,
            enabled = accessGranted && state.song != null && !state.fetching,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sync),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.retry_match))
        }
    }
}

@Composable
internal fun BridgeContent(
    contentPadding: PaddingValues,
    state: BridgeUiState,
    accessGranted: Boolean,
    playbackActions: PlaybackActions,
    onOpenPermissionGuide: () -> Unit,
    onOpenMatchDetails: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding),
    ) {
        val useTwoColumns = maxWidth >= 840.dp && maxHeight >= 480.dp
        ScrollableContent(
            state = state,
            accessGranted = accessGranted,
            playbackActions = playbackActions,
            useTwoColumns = useTwoColumns,
            onOpenPermissionGuide = onOpenPermissionGuide,
            onOpenMatchDetails = onOpenMatchDetails,
        )
    }
}

@Composable
private fun ScrollableContent(
    state: BridgeUiState,
    accessGranted: Boolean,
    playbackActions: PlaybackActions,
    useTwoColumns: Boolean,
    onOpenPermissionGuide: () -> Unit,
    onOpenMatchDetails: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 1120.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ScreenIntroduction()
            if (!accessGranted) {
                AccessPrompt(onOpenPermissionGuide)
            }
            ContentCards(
                state = state,
                playbackActions = playbackActions,
                useTwoColumns = useTwoColumns,
                onOpenMatchDetails = onOpenMatchDetails,
            )
            Text(
                text = stringResource(R.string.follow_detail),
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScreenIntroduction() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.tagline_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AccessPrompt(onOpenPermissionGuide: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.connect_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.connect_detail),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onOpenPermissionGuide) {
                Text(stringResource(R.string.permission_guide))
            }
        }
    }
}

@Composable
private fun ContentCards(
    state: BridgeUiState,
    playbackActions: PlaybackActions,
    useTwoColumns: Boolean,
    onOpenMatchDetails: () -> Unit
) {
    val nowPlaying: @Composable () -> Unit = {
        NowPlayingCard(
            state = state,
            canPlayPause = playbackActions.canPlayPause,
            canSkipPrevious = playbackActions.canSkipPrevious,
            canSkipNext = playbackActions.canSkipNext,
            onPlayPause = playbackActions.onPlayPause,
            onPrevious = playbackActions.onPrevious,
            onNext = playbackActions.onNext,
            canSeek = playbackActions.canSeek,
            onSeek = playbackActions.onSeek
        )
    }
    val match: @Composable () -> Unit = {
        MatchCard(state, onOpenMatchDetails)
    }

    if (useTwoColumns) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                modifier = Modifier.weight(0.44f),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                nowPlaying()
                match()
            }
            LyricCard(state, Modifier.weight(0.56f))
        }
    } else {
        nowPlaying()
        LyricCard(state)
        match()
    }
}
