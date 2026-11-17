package com.ryan1202.lyricsbridge

import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class QqLyricsFlowTest {
    @Test fun currentSongMatchesQqOriginalAndPublishesLyrics() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun status(message: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$message\n") })
        status("Connecting to Apple Music session")
        val source = context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaAccessService::class.java))
            .first { it.packageName == "com.apple.android.music" }
        val initial = TrackInfo.from(source.metadata)!!
        status("Native track: ${initial.title}; refreshing metadata")
        assertEquals("无中生有", initial.title)
        fun await(message: String, timeoutMs: Long, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
            assertTrue(message, condition())
        }
        source.transportControls.skipToNext()
        await("Apple Music must refresh its native metadata before testing", 8_000) {
            TrackInfo.from(source.metadata)?.let { it.identity != initial.identity } == true
        }
        source.transportControls.skipToPrevious()
        await("Return to the original Apple Music song", 8_000) {
            TrackInfo.from(source.metadata)?.id == initial.id
        }
        val song = TrackInfo.from(source.metadata)!!
        status("Native track refreshed; querying lyric providers")
        val fixture = File(context.cacheDir, "qq-lyrics-test.json")
        val lyric = try { LyricRepository(fixture).find(song, refresh = true) } finally { fixture.delete() }
        status("Provider result: ${lyric.source}; ${lyric.match}")
        assertEquals("QQ音乐", lyric.source)
        assertEquals("无中生有 Ex Nihilo", lyric.match.substringBefore(" · "))
        assertTrue(lyric.guessed)
        assertTrue(LrcTimeline(lyric.raw).lines.size > 20)
        val queueKey = "com.apple.android.music.playback.metadata.ITEM_QUEUE_ID"
        val observed = AtomicReference(source.metadata)
        val queueId = observed.get()?.getLong(queueKey)
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { observed.set(metadata) }
        }
        source.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            context.startForegroundService(Intent(context, BridgeService::class.java).setAction("auto"))
            await("Synchronizer must connect to the current song", 15_000) {
                BridgeStatus.ui.value.running && BridgeStatus.ui.value.song?.id == song.id && !BridgeStatus.ui.value.fetching
            }
            context.startForegroundService(Intent(context, BridgeService::class.java).setAction("retry"))
            status("Waiting for QQ lyrics in original media session")
            await("QQ lyrics must reach the original Apple Music session: ${BridgeStatus.summary}", 60_000) {
                observed.get()?.getString(AppleMusicLyricsWriter.WHOLE) == LyricPublication.from(lyric).raw &&
                    BridgeStatus.ui.value.provider == "QQ音乐" && !BridgeStatus.ui.value.fetching
            }
            assertEquals(song.title, observed.get()?.getString(MediaMetadata.METADATA_KEY_TITLE))
            assertEquals(song.artist, observed.get()?.getString(MediaMetadata.METADATA_KEY_ARTIST))
            assertEquals(queueId, observed.get()?.getLong(queueKey))
            assertTrue(BridgeStatus.ui.value.guessed)
            status("QQ original matched: ${lyric.match}; timed lines=${LrcTimeline(lyric.raw).lines.size}; title, artist and queue identity preserved.")
        } finally { source.unregisterCallback(callback) }
    }
}
