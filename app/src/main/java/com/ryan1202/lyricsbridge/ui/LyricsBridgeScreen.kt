package com.ryan1202.lyricsbridge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.ryan1202.lyricsbridge.BridgeUiState
import com.ryan1202.lyricsbridge.TrackInfo

@Composable
fun LyricsBridgeScreen(
    state: BridgeUiState,
    accessGranted: Boolean,
    notificationsGranted: Boolean,
    onToggleSync: () -> Unit,
    onRetry: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenAccessSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    islandLyricsEnabled: Boolean = false,
    onIslandLyricsChange: (Boolean) -> Unit = {},
    canPlayPause: Boolean = false,
    canSkipPrevious: Boolean = false,
    canSkipNext: Boolean = false,
    onPlayPause: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    canSeek: Boolean = false,
    onSeek: (Long) -> Unit = {},
    languageTag: String = "",
    onLanguageChange: (String) -> Unit = {}
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var permissionGuideOpen by rememberSaveable { mutableStateOf(false) }
    var matchDetailsOpen by remember { mutableStateOf(false) }
    val playbackActions = PlaybackActions(
        canPlayPause = canPlayPause,
        canSkipPrevious = canSkipPrevious,
        canSkipNext = canSkipNext,
        canSeek = canSeek,
        onPlayPause = onPlayPause,
        onPrevious = onPrevious,
        onNext = onNext,
        onSeek = onSeek
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BridgeTopBar(onOpenSettings = { settingsOpen = true })
        },
        bottomBar = {
            BridgeBottomBar(
                state = state,
                accessGranted = accessGranted,
                onToggleSync = {
                    if (!state.running && !accessGranted) permissionGuideOpen = true
                    else onToggleSync()
                },
                onOpenMusic = onOpenMusic,
                onRetry = onRetry
            )
        }
    ) { contentPadding ->
        BridgeContent(
            contentPadding = contentPadding,
            state = state,
            accessGranted = accessGranted,
            playbackActions = playbackActions,
            onOpenPermissionGuide = { permissionGuideOpen = true },
            onOpenMatchDetails = { matchDetailsOpen = true }
        )
    }

    if (permissionGuideOpen) {
        PermissionGuide(
            accessGranted = accessGranted,
            notificationsGranted = notificationsGranted,
            onOpenAccessSettings = onOpenAccessSettings,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenBatterySettings = onOpenBatterySettings,
            onOpenSystemSettings = onOpenSystemSettings,
            onDismiss = { permissionGuideOpen = false }
        )
    } else if (settingsOpen) {
        SettingsSheet(
            languageTag = languageTag,
            onOpenPermissionGuide = { permissionGuideOpen = true },
            onLanguageChange = onLanguageChange,
            islandLyricsEnabled = islandLyricsEnabled,
            onIslandLyricsChange = onIslandLyricsChange,
            onDismiss = { settingsOpen = false }
        )
    }

    if (matchDetailsOpen) {
        MatchDetailsDialog(
            state = state,
            onDismiss = { matchDetailsOpen = false }
        )
    }
}

@Preview(name = "浅色 · 手机", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LightPreview() {
    LyricsBridgeTheme(darkTheme = false) {
        LyricsBridgeScreen(
            state = BridgeUiState(),
            accessGranted = true,
            notificationsGranted = true,
            onToggleSync = {},
            onRetry = {},
            onOpenMusic = {},
            onOpenAccessSettings = {},
            onOpenNotificationSettings = {},
            onOpenBatterySettings = {},
            onOpenSystemSettings = {},
        )
    }
}

@Preview(name = "深色 · 同步中", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DarkPreview() {
    val previewState = BridgeUiState(
        running = true,
        song = TrackInfo(
            id = "preview",
            title = "此刻的音乐",
            artist = "Apple Music",
            album = "正在聆听",
            durationMs = 210_000,
        ),
        positionMs = 62_000,
        lyric = "让每一句歌词，跟上此刻的音乐。",
        provider = "网易云音乐",
        status = "歌词已同步",
    )
    LyricsBridgeTheme(darkTheme = true) {
        LyricsBridgeScreen(
            state = previewState,
            accessGranted = true,
            notificationsGranted = true,
            onToggleSync = {},
            onRetry = {},
            onOpenMusic = {},
            onOpenAccessSettings = {},
            onOpenNotificationSettings = {},
            onOpenBatterySettings = {},
            onOpenSystemSettings = {},
        )
    }
}
