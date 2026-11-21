package com.ryan1202.lyricsbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal const val SYNC_NOTIFICATION_CHANNEL = "sync"

class BridgeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val network = Executors.newSingleThreadExecutor()
    private lateinit var manager: MediaSessionManager
    private lateinit var notifications: NotificationManager
    private lateinit var repository: LyricRepository
    private lateinit var writer: AppleMusicLyricsWriter
    private var source: MediaController? = null
    private var sourceMetadata: android.media.MediaMetadata? = null
    private var sourcePlayback: PlaybackState? = null
    private var track: TrackInfo? = null
    private var result: LyricResult? = null
    private var timeline: LrcTimeline? = null
    private var publication = LyricPublication.empty
    private var fetch: Future<*>? = null
    private var epoch = 0
    private var fetching = false
    private var fetchError: String? = null
    private var noticeText = ""
    private var running = false
    private var destroyed = false
    private val listenerComponent by lazy { ComponentName(this, MediaAccessService::class.java) }
    private val sourceCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) { update() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { update() }
        override fun onSessionDestroyed() { selectSource() }
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { selectSource() }
    private val tick = object : Runnable {
        override fun run() { update() }
    }
    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(MediaSessionManager::class.java)
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(SYNC_NOTIFICATION_CHANNEL, localizedString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
        notifications.cancel(2)
        repository = LyricRepository(File(filesDir, "lyrics-cache.json"))
        writer = AppleMusicLyricsWriter(this) { update() }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            SyncPreference.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if ((intent == null || intent.action == "auto") && !SyncPreference.enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == "language") {
            refreshLanguage()
            return START_STICKY
        }
        val notice = notification(localizedString(R.string.connecting_music))
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, notice)
        if (!running) {
            running = true
            BridgeStatus.mode = "follow"
            try {
                manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
                selectSource()
            } catch (_: SecurityException) {
                BridgeStatus.summary = localizedString(R.string.enable_access_first)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (intent?.action == "retry") track?.let { load(it, true) }
        if (intent?.action == "island") update()
        return START_STICKY
    }
    private fun selectSource() {
        try {
            val candidates = manager.getActiveSessions(listenerComponent).filter { it.packageName == "com.apple.android.music" }
            val next = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: candidates.firstOrNull()
            if (source?.sessionToken != next?.sessionToken) {
                source?.unregisterCallback(sourceCallback)
                source = next
                sourceMetadata = next?.metadata
                sourcePlayback = next?.playbackState
                source?.registerCallback(sourceCallback, handler)
                writer.attach(next, sourceMetadata, sourcePlayback)
                epoch++
                fetch?.cancel(true)
                fetching = false
                fetchError = null
                track = null
                result = null
                timeline = null
                publication = LyricPublication.empty
            }
            update()
        } catch (_: SecurityException) {
            BridgeStatus.summary = localizedString(R.string.access_revoked)
            stopSelf()
        }
    }
    private fun load(song: TrackInfo, refresh: Boolean = false) {
        fetch?.cancel(true)
        val requestEpoch = ++epoch
        track = song
        result = null
        publication = LyricPublication.matching(localizedString(R.string.matching))
        timeline = publication.timeline
        fetching = true
        fetchError = null
        fetch = network.submit {
            val outcome = runCatching { repository.find(song, refresh) }
            handler.post {
                if (!destroyed && requestEpoch == epoch) {
                    fetching = false
                    outcome.onSuccess {
                        result = it
                        publication = publicationFor(it)
                        timeline = publication.timeline
                    }.onFailure {
                        publication = LyricPublication.empty
                        timeline = null
                        fetchError = it.message ?: localizedString(R.string.fetch_failed)
                    }
                    update()
                }
            }
        }
    }
    private fun publicationFor(result: LyricResult) = LyricPublication.from(result,
        localizedString(R.string.guess_notice), localizedString(R.string.instrumental))

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshLanguage()
    }

    private fun refreshLanguage() {
        notifications.createNotificationChannel(NotificationChannel(SYNC_NOTIFICATION_CHANNEL, localizedString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
        publication = result?.let { publicationFor(it) }
            ?: if (fetching) LyricPublication.matching(localizedString(R.string.matching)) else LyricPublication.empty
        timeline = publication.timeline
        noticeText = ""
        update()
    }

    private fun position(): Long {
        writer.currentPosition(track)?.let { return it }
        val state = sourcePlayback ?: return 0
        val elapsed = if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0)
            ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong() else 0L
        return (state.position + elapsed).coerceAtLeast(0)
    }
    private fun update() {
        if (!running || destroyed) return
        sourceMetadata = source?.metadata
        sourcePlayback = source?.playbackState
        writer.refresh(sourceMetadata, sourcePlayback)
        val metadata = sourceMetadata
        val song = writer.currentTrack()
        if (song != null && song.needsLyricsLookup(track)) load(song)
        if (song == null && track != null) {
            epoch++
            fetch?.cancel(true)
            track = null
            result = null
            timeline = null
            publication = LyricPublication.empty
            fetching = false
            fetchError = null
        }
        val pos = position()
        val line = timeline?.at(pos).orEmpty()
        val state = sourcePlayback?.state
        val playback = if (state == PlaybackState.STATE_PLAYING) localizedString(R.string.playing) else localizedString(R.string.paused)
        val lyricStatus = publication.status
        val raw = publication.raw
        val islandLyrics = SyncPreference.islandLyricsEnabled(this) && result?.instrumental == false
        val published = song != null && writer.isPublished(metadata, song, raw, line, lyricStatus, islandLyrics)
        val writeStatus = when {
            published -> localizedString(R.string.lyrics_written)
            song != null -> writer.write(song, raw, line, lyricStatus, islandLyrics)
            else -> localizedString(R.string.waiting_apple_music)
        }
        val fetchStatus = when {
            fetching -> localizedString(R.string.searching_match)
            fetchError != null -> localizedString(R.string.fetch_failed_details, fetchError!!)
            result?.instrumental == true -> localizedString(R.string.instrumental)
            result != null -> localizedString(R.string.matched_details, result!!.source, result!!.match)
            else -> localizedString(R.string.waiting_track)
        }
        BridgeStatus.summary = if (song == null) localizedString(R.string.choose_song) else
            localizedString(R.string.summary_details, song.title, song.artist, playback, pos / 1000, song.durationMs / 1000, fetchStatus, writeStatus)
        BridgeStatus.lyric = when {
            song == null -> ""
            fetching -> localizedString(R.string.matching)
            result?.instrumental == true -> localizedString(R.string.instrumental)
            result == null -> localizedString(R.string.no_lyrics)
            line.isBlank() -> "···"
            else -> line
        }
        val lines = timeline?.lines.orEmpty()
        val currentIndex = lines.indexOfLast { it.timeMs <= pos }
        BridgeStatus.ui.value = BridgeUiState(
            running = true, song = song,
            artwork = if (writer.matchesPlatformTrack(metadata, song)) {
                metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            } else null,
            playing = state == PlaybackState.STATE_PLAYING, positionMs = pos, fetching = fetching,
            lyric = BridgeStatus.lyric,
            lyricTimeMs = lines.getOrNull(currentIndex)?.timeMs,
            previousLyric = if (currentIndex > 0) lines.take(currentIndex).lastOrNull { it.text.isNotBlank() }?.text.orEmpty() else "",
            nextLyric = lines.drop(currentIndex + 1).firstOrNull { it.text.isNotBlank() }?.text.orEmpty(),
            provider = result?.source.orEmpty(), match = result?.match.orEmpty(), guessed = result?.guessed == true, error = fetchError?.let { localizedString(R.string.fetch_failed) },
            status = when {
                song == null -> localizedString(R.string.waiting_playback)
                fetching -> localizedString(R.string.matching_lyrics)
                fetchError != null -> localizedString(R.string.lyrics_unavailable)
                result?.instrumental == true -> localizedString(R.string.instrumental)
                result != null && writeStatus == localizedString(R.string.lyrics_written) -> localizedString(R.string.lyrics_synced)
                result != null -> localizedString(R.string.syncing_lyrics)
                else -> localizedString(R.string.waiting_lyrics)
            }
        )
        val text = when {
            song == null -> localizedString(R.string.waiting_apple_music)
            fetching -> localizedString(R.string.fetching_lyrics)
            result == null -> localizedString(R.string.notification_no_match)
            else -> localizedString(R.string.notification_syncing)
        }
        if (text != noticeText) {
            noticeText = text
            notifications.notify(1, notification(text))
        }
        handler.removeCallbacks(tick)
        val boundaryDelay = if (state == PlaybackState.STATE_PLAYING) {
            timeline?.delayUntilNextLine(pos, sourcePlayback?.playbackSpeed ?: 1f)
        } else null
        val writeDelay = if (writeStatus == localizedString(R.string.writing_lyrics)) writer.retryDelayMs() else null
        // Keep observing active sessions after the last lyric, including while paused.
        val sessionDelay = if (source != null) 1_000L else null
        listOfNotNull(boundaryDelay, writeDelay, sessionDelay).minOrNull()?.let { handler.postDelayed(tick, it) }
    }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, BridgeService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, SYNC_NOTIFICATION_CHANNEL).setSmallIcon(R.drawable.ic_brand)
            .setContentTitle(localizedString(R.string.notification_title)).setContentText(text).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .addAction(Notification.Action.Builder(null, localizedString(R.string.pause_sync), stop).build()).build()
    }
    override fun onDestroy() {
        destroyed = true
        epoch++
        handler.removeCallbacks(tick)
        fetch?.cancel(true)
        network.shutdownNow()
        manager.removeOnActiveSessionsChangedListener(sessionsListener)
        source?.unregisterCallback(sourceCallback)
        writer.detach()
        BridgeStatus.summary = localizedString(R.string.sync_stopped)
        BridgeStatus.lyric = ""
        BridgeStatus.mode = ""
        BridgeStatus.ui.value = BridgeStatus.ui.value.copy(running = false, fetching = false, lyric = "", previousLyric = "", nextLyric = "", error = null, status = localizedString(R.string.sync_stopped))
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
