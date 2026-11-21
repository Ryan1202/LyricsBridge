package com.ryan1202.lyricsbridge

import android.content.Context
import android.media.session.MediaController as PlatformController
import android.media.session.MediaSession
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class AppleMusicLyricsWriter(private val context: Context, private val onReady: () -> Unit) {
    private val executor = ContextCompat.getMainExecutor(context)
    private var platform: PlatformController? = null
    private var platformMetadata: MediaMetadata? = null
    private var platformPlayback: PlaybackState? = null
    private var controller: MediaController? = null
    private var token: MediaSession.Token? = null
    private var generation = 0
    private var connecting = false
    private var retryAt = 0L
    private var lastWrite = 0L
    private var writing = false
    private var writeGeneration = 0
    private var observedTrack: String? = null
    private var positionTrack: String? = null
    private var lastPosition: Long? = null
    private var mismatchSince = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var originalExtras: Bundle? = null
    private var originalTrack: TrackInfo? = null
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) {
                val identity = currentTrack()?.identity
                if (identity != null && identity != observedTrack) {
                    observedTrack = identity
                    writeGeneration++
                    writing = false
                    originalTrack = null
                    originalExtras = null
                    lastWrite = 0
                }
                onReady()
            }
        }
    }
    private var statusId = R.string.waiting_session
    private var connectionError = ""
    val status: String
        get() = if (statusId == R.string.connection_failed) context.localizedString(statusId, connectionError)
            else context.localizedString(statusId)

    fun attach(next: PlatformController?, metadata: MediaMetadata? = next?.metadata, playback: PlaybackState? = next?.playbackState) {
        if (next?.sessionToken == token) return
        detach()
        platform = next
        platformMetadata = metadata
        platformPlayback = playback
        token = next?.sessionToken
        retryAt = 0
        connect()
    }
    fun refresh(metadata: MediaMetadata?, playback: PlaybackState?) {
        platformMetadata = metadata
        platformPlayback = playback
        val native = nativeTrack()
        if (native != null && metadata != null && !matchesPlatformTrack(metadata, native)) {
            writeGeneration++
            writing = false
            val now = SystemClock.elapsedRealtime()
            if (mismatchSince == 0L) mismatchSince = now
            else if (now - mismatchSince >= 1_000) {
                val stale = controller
                controller = null
                stale?.removeListener(playerListener)
                stale?.release()
                mismatchSince = 0
            }
        } else mismatchSince = 0
        connect()
    }
    private fun connect() {
        val source = platform ?: return
        if (connecting || controller != null || SystemClock.elapsedRealtime() < retryAt) return
        connecting = true
        statusId = R.string.connecting_music
        val epoch = generation
        val tokenFuture = SessionToken.createSessionToken(context, source.sessionToken)
        tokenFuture.addListener({
            if (epoch == generation) {
                try {
                    val future = MediaController.Builder(context, tokenFuture.get())
                        .setListener(object : MediaController.Listener {
                            override fun onDisconnected(disconnected: MediaController) {
                                if (epoch == generation && controller === disconnected) {
                                    controller = null
                                    connecting = false
                                    retryAt = 0
                                    statusId = R.string.reconnecting_music
                                    connect()
                                    onReady()
                                }
                            }
                        }).buildAsync()
                    future.addListener({
                        try {
                            val connected = future.get()
                            if (epoch != generation) connected.release() else {
                                controller = connected
                                observedTrack = currentTrack()?.identity
                                connected.addListener(playerListener)
                                connecting = false
                                statusId = R.string.music_connected
                                onReady()
                            }
                        } catch (e: Exception) { if (epoch == generation) failed(e) }
                    }, executor)
                } catch (e: Exception) { failed(e) }
            }
        }, executor)
    }
    private fun failed(error: Exception) {
        connecting = false
        retryAt = SystemClock.elapsedRealtime() + 5_000
        statusId = R.string.connection_failed
        connectionError = error.cause?.message ?: error.message.orEmpty()
        onReady()
    }
    fun retryDelayMs(): Long = if (writing) 100 else (750 - (SystemClock.elapsedRealtime() - lastWrite)).coerceAtLeast(1)
    private fun sameTrack(item: MediaItem, track: TrackInfo): Boolean {
        val metadata = item.mediaMetadata
        return (track.id.isEmpty() || item.mediaId.isEmpty() || item.mediaId == track.id) &&
            SongMatcher.normalize(metadata.title?.toString().orEmpty()) == SongMatcher.normalize(track.title) &&
            SongMatcher.normalize(metadata.artist?.toString().orEmpty()) == SongMatcher.normalize(track.artist)
    }
    private fun nativeTrack(): TrackInfo? {
        val connected = controller ?: return null
        val item = connected.currentMediaItem ?: return null
        val metadata = item.mediaMetadata
        val title = metadata.title?.toString().orEmpty()
        val artist = metadata.artist?.toString().orEmpty()
        if (title.isBlank() || artist.isBlank()) return null
        return TrackInfo(item.mediaId, title, artist, metadata.albumTitle?.toString().orEmpty(),
            (metadata.durationMs ?: connected.duration).coerceAtLeast(0))
    }
    fun currentTrack(): TrackInfo? = TrackInfo.current(nativeTrack(), TrackInfo.from(platformMetadata),
        titleIsLyric(platformMetadata))
    fun currentPosition(track: TrackInfo?): Long? {
        val connected = controller ?: return null
        val item = connected.currentMediaItem ?: return null
        if (track == null || !sameTrack(item, track)) return null
        return connected.currentPosition.coerceAtLeast(0)
    }
    fun matchesPlatformTrack(metadata: MediaMetadata?, track: TrackInfo?): Boolean {
        return track?.matchesSessionTrack(TrackInfo.from(metadata), titleIsLyric(metadata)) == true
    }
    private fun titleIsLyric(metadata: MediaMetadata?): Boolean {
        val line = metadata?.getString(LINE)
        return !line.isNullOrBlank() && metadata.getString(MediaMetadata.METADATA_KEY_TITLE) == line
    }
    fun isPublished(metadata: MediaMetadata?, track: TrackInfo, raw: String, line: String,
                    lyricStatus: Long, islandLyrics: Boolean): Boolean {
        val title = if (islandLyrics && lyricStatus == 0L && line.isNotBlank()) line else track.title
        return matchesPlatformTrack(metadata, track) &&
            metadata?.getString(WHOLE) == raw && metadata.getString(LINE) == line &&
            metadata.getLong(STATUS) == lyricStatus && metadata.getString(MediaMetadata.METADATA_KEY_TITLE) == title
    }
    fun write(track: TrackInfo, raw: String, line: String, lyricStatus: Long, islandLyrics: Boolean = false): String {
        refresh(platform?.metadata, platform?.playbackState)
        val connected = controller ?: return status
        if (!connected.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return context.localizedString(R.string.update_not_allowed)
        val item = connected.currentMediaItem ?: return context.localizedString(R.string.waiting_media_item)
        if (!sameTrack(item, track)) return context.localizedString(R.string.waiting_track_change)
        val position = platformPlayback?.position?.takeIf { it >= 0 }
        val previousPosition = lastPosition
        val positionReset = positionTrack == track.identity && position != null && previousPosition != null &&
            position + 1_000 < previousPosition
        positionTrack = track.identity
        lastPosition = position
        if (positionReset) {
            writeGeneration++
            writing = false
            lastWrite = SystemClock.elapsedRealtime()
            return context.localizedString(R.string.writing_lyrics)
        }
        if (originalTrack?.identity != track.identity) {
            originalTrack = track
            originalExtras = Bundle(item.mediaMetadata.extras ?: Bundle())
            lastWrite = 0
        }
        val current = platformMetadata
        if (!matchesPlatformTrack(current, track)) return context.localizedString(R.string.waiting_track_change)
        if (isPublished(current, track, raw, line, lyricStatus, islandLyrics)) {
            return context.localizedString(R.string.lyrics_written)
        }
        if (writing || SystemClock.elapsedRealtime() - lastWrite < 750) return context.localizedString(R.string.writing_lyrics)
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle())
        extras.putString(WHOLE, raw)
        extras.putString(LINE, line)
        extras.putLong(STATUS, lyricStatus)
        if (islandLyrics && lyricStatus == 0L && line.isNotBlank()) {
            extras.putString(MediaMetadata.METADATA_KEY_TITLE, line)
        } else extras.remove(MediaMetadata.METADATA_KEY_TITLE)
        val epoch = generation
        val writeEpoch = writeGeneration
        writing = true
        lastWrite = SystemClock.elapsedRealtime()
        // This Media3 field triggers metadata equality checks without clearing Apple's queue extras.
        connected.replaceMediaItem(connected.currentMediaItemIndex, replacement(item, extras, refresh = true))
        handler.postDelayed({
            if (epoch == generation && writeEpoch == writeGeneration) {
                val latest = connected.currentMediaItem
                val latestMetadata = platform?.metadata
                val latestPosition = platform?.playbackState?.position
                val positionConsistent = position == null || latestPosition == null || latestPosition + 1_000 >= position
                if (latest != null && sameTrack(latest, track) && matchesPlatformTrack(latestMetadata, track) && positionConsistent) {
                    connected.replaceMediaItem(connected.currentMediaItemIndex, replacement(latest, extras))
                }
                writing = false
                onReady()
            }
        }, 100)
        return context.localizedString(R.string.writing_lyrics)
    }
    private fun replacement(item: MediaItem, extras: Bundle, refresh: Boolean = false): MediaItem {
        val commands = item.mediaMetadata.supportedCommands.filter { it != REFRESH_COMMAND }
        return item.buildUpon()
            .setUri(Uri.parse("content://com.ryan1202.lyricsbridge/metadata-only"))
            .setRequestMetadata(MediaItem.RequestMetadata.EMPTY)
            .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras)
                .setSupportedCommands(if (refresh) commands + REFRESH_COMMAND else commands).build()).build()
    }

    fun detach() {
        generation++
        val old = controller
        val oldPlatform = platform
        val oldPosition = oldPlatform?.playbackState?.position
        val detachGeneration = generation
        old?.removeListener(playerListener)
        val item = old?.currentMediaItem
        val track = originalTrack
        val saved = originalExtras
        if (old != null && item != null && track != null && saved != null && sameTrack(item, track) &&
            matchesPlatformTrack(oldPlatform?.metadata, track) &&
            old.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
            val extras = Bundle(item.mediaMetadata.extras ?: saved)
            extras.remove(MediaMetadata.METADATA_KEY_TITLE)
            for (key in listOf(WHOLE, LINE, STATUS)) extras.remove(key)
            if (saved.containsKey(WHOLE)) extras.putCharSequence(WHOLE, saved.getCharSequence(WHOLE))
            if (saved.containsKey(LINE)) extras.putCharSequence(LINE, saved.getCharSequence(LINE))
            if (saved.containsKey(STATUS)) extras.putLong(STATUS, saved.getLong(STATUS))
            old.replaceMediaItem(old.currentMediaItemIndex, replacement(item, extras, refresh = true))
            handler.postDelayed({
                val latest = old.currentMediaItem
                val position = oldPlatform?.playbackState?.position
                val positionConsistent = oldPosition == null || position == null || position + 1_000 >= oldPosition
                if (detachGeneration == generation && latest != null && sameTrack(latest, track) &&
                    matchesPlatformTrack(oldPlatform?.metadata, track) && positionConsistent) {
                    old.replaceMediaItem(old.currentMediaItemIndex, replacement(latest, extras))
                }
                handler.postDelayed({ old.release() }, 400)
            }, 100)
        } else old?.release()
        writing = false
        controller = null
        platform = null
        platformMetadata = null
        platformPlayback = null
        token = null
        connecting = false
        originalExtras = null
        originalTrack = null
        observedTrack = null
        positionTrack = null
        lastPosition = null
        mismatchSince = 0
        statusId = R.string.waiting_session
    }
    companion object {
        private const val REFRESH_COMMAND = "com.ryan1202.lyricsbridge.REFRESH_LYRICS"
        const val WHOLE = "ucar.media.metadata.LYRICS_WHOLE"
        const val LINE = "ucar.media.metadata.LYRICS_LINE"
        const val STATUS = "ucar.media.metadata.LYRICS_STATUS"
    }
}
