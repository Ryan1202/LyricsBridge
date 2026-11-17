package com.ryan1202.lyricsbridge

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan1202.lyricsbridge.ui.LyricsBridgeScreen
import com.ryan1202.lyricsbridge.ui.LyricsBridgeTheme

class MainActivity : AppCompatActivity() {
    private var accessGranted by mutableStateOf(false)
    private var notificationsGranted by mutableStateOf(false)
    private var playbackActions by mutableStateOf(0L)
    private var mediaPlaying by mutableStateOf(false)
    private var musicController: MediaController? = null
    private var listeningForSessions = false
    private var visible = false
    private var playbackSnapshot: PlaybackState? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            val playback = playbackSnapshot ?: return
            if (!visible || !mediaPlaying) return
            val elapsed = if (playback.lastPositionUpdateTime > 0)
                ((SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime) * playback.playbackSpeed).toLong() else 0
            val ui = BridgeStatus.ui.value
            val position = (playback.position + elapsed).coerceAtLeast(0)
            BridgeStatus.ui.value = ui.copy(positionMs = if ((ui.song?.durationMs ?: 0) > 0) position.coerceAtMost(ui.song!!.durationMs) else position)
            progressHandler.postDelayed(this, 500)
        }
    }
    private val sessionManager by lazy { getSystemService(MediaSessionManager::class.java) }
    private val listenerComponent by lazy { ComponentName(this, MediaAccessService::class.java) }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { selectController() }
    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { refreshPlayback() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { refreshPlayback() }
        override fun onSessionDestroyed() { selectController() }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by BridgeStatus.ui.collectAsStateWithLifecycle()
            LyricsBridgeTheme {
                LyricsBridgeScreen(
                    state = state.copy(playing = mediaPlaying),
                    accessGranted = accessGranted,
                    notificationsGranted = notificationsGranted,
                    onToggleSync = {
                        if (state.running) {
                            SyncPreference.setEnabled(this, false)
                            stopService(Intent(this, BridgeService::class.java))
                        }
                        else if (!accessGranted) openAccessSettings()
                        else startSync("follow")
                    },
                    onRetry = { startSync("retry") },
                    onOpenMusic = { openMusic() },
                    onGrantAccess = { openAccessSettings() },
                    onGrantNotifications = { requestNotifications() },
                    canPlayPause = playbackActions and (PlaybackState.ACTION_PLAY_PAUSE or
                        if (mediaPlaying) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY) != 0L,
                    canSkipPrevious = playbackActions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                    canSkipNext = playbackActions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                    onPlayPause = {
                        musicController?.let {
                            if (it.playbackState?.state == PlaybackState.STATE_PLAYING) it.transportControls.pause()
                            else it.transportControls.play()
                        }
                    },
                    onPrevious = { musicController?.transportControls?.skipToPrevious() },
                    onNext = { musicController?.transportControls?.skipToNext() },
                    canSeek = playbackActions and PlaybackState.ACTION_SEEK_TO != 0L,
                    onSeek = { position -> musicController?.transportControls?.seekTo(position) },
                    languageTag = AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty(),
                    onLanguageChange = { tag ->
                        if (Build.VERSION.SDK_INT < 33) getSharedPreferences("language", MODE_PRIVATE).edit().putString("tag", tag).apply()
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }
                )
            }
        }
    }
    private fun openAccessSettings() { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
    private fun refreshPermissions() {
        accessGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        notificationsGranted = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
    private fun openMusic() {
        packageManager.getLaunchIntentForPackage("com.apple.android.music")?.let { startActivity(it) }
            ?: Toast.makeText(this, getString(R.string.music_not_installed), Toast.LENGTH_LONG).show()
    }
    private fun startSync(action: String) {
        if (!accessGranted) { openAccessSettings(); return }
        SyncPreference.setEnabled(this, true)
        startForegroundService(Intent(this, BridgeService::class.java).setAction(action))
    }
    override fun onResume() {
        super.onResume()
        visible = true
        if (BridgeStatus.ui.value.running) startService(Intent(this, BridgeService::class.java).setAction("language"))
        refreshPermissions()
        if (accessGranted) {
            try {
                sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
                listeningForSessions = true
                selectController()
                if (SyncPreference.enabled(this) && !BridgeStatus.ui.value.running) {
                    startForegroundService(Intent(this, BridgeService::class.java).setAction("auto"))
                }
            } catch (_: SecurityException) { refreshPermissions() }
        }
    }
    private fun selectController() {
        try {
            val candidates = sessionManager.getActiveSessions(listenerComponent)
                .filter { it.packageName == "com.apple.android.music" }
            val next = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: candidates.firstOrNull()
            if (musicController?.sessionToken != next?.sessionToken) {
                musicController?.unregisterCallback(mediaCallback)
                musicController = next
                next?.registerCallback(mediaCallback)
            }
            refreshPlayback()
        } catch (_: SecurityException) {
            musicController?.unregisterCallback(mediaCallback)
            musicController = null
            playbackActions = 0L
            mediaPlaying = false
            refreshPermissions()
        }
    }
    private fun refreshPlayback() {
        val source = musicController
        val playback = source?.playbackState
        playbackSnapshot = playback
        playbackActions = playback?.actions ?: 0L
        mediaPlaying = playback?.state == PlaybackState.STATE_PLAYING
        progressHandler.removeCallbacks(progressTick)
        if (visible && mediaPlaying) progressHandler.postDelayed(progressTick, 500)
        if (!BridgeStatus.ui.value.running) {
            val metadata = source?.metadata
            BridgeStatus.ui.value = BridgeUiState(
                song = TrackInfo.from(metadata),
                artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
                playing = mediaPlaying,
                positionMs = playback?.position?.coerceAtLeast(0) ?: 0
            )
        }
    }
    override fun onPause() {
        visible = false
        progressHandler.removeCallbacks(progressTick)
        playbackSnapshot = null
        musicController?.unregisterCallback(mediaCallback)
        musicController = null
        playbackActions = 0L
        mediaPlaying = false
        if (listeningForSessions) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            listeningForSessions = false
        }
        super.onPause()
    }
}
