package com.ryan1202.lyricsbridge

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppleMusicIpcTest {
    @Test fun updatesLyricLinesAndRestoresWithoutChangingTitle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaAccessService::class.java))
            .first { it.packageName == "com.apple.android.music" }
        val staleIdentity = TrackInfo.from(source.metadata)?.identity
        source.transportControls.skipToNext()
        val refreshDeadline = android.os.SystemClock.elapsedRealtime() + 8_000
        while (TrackInfo.from(source.metadata)?.identity == staleIdentity && android.os.SystemClock.elapsedRealtime() < refreshDeadline) Thread.sleep(100)
        assertNotEquals("Refresh Apple Music's current song before writing lyrics", staleIdentity, TrackInfo.from(source.metadata)?.identity)
        val ready = CountDownLatch(1)
        lateinit var writer: AppleMusicLyricsWriter
        instrumentation.runOnMainSync {
            writer = AppleMusicLyricsWriter(context) { ready.countDown() }
            writer.attach(source)
        }
        assertTrue("Apple Music connection timed out", ready.await(10, TimeUnit.SECONDS))
        val track = TrackInfo.from(source.metadata)!!
        val originalWhole = source.metadata?.getString(AppleMusicLyricsWriter.WHOLE)
        val originalLine = source.metadata?.getString(AppleMusicLyricsWriter.LINE)
        val queueIdKey = "com.apple.android.music.playback.metadata.ITEM_QUEUE_ID"
        val originalQueueId = source.metadata?.getLong(queueIdKey)
        val raw = "[00:00.00]IPC first line\n[00:01.00]IPC second line"
        fun publish(line: String) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
            var lastStatus = ""
            do {
                var status = ""
                var matches = false
                instrumentation.runOnMainSync {
                    status = writer.write(track, raw, line, 0)
                    matches = writer.currentTrack()?.identity == track.identity
                }
                if (status != lastStatus) {
                    lastStatus = status
                    instrumentation.sendStatus(0, android.os.Bundle().apply {
                        putString("stream", "Lyric update: $status; Media3 track matches=$matches\n")
                    })
                }
                Thread.sleep(150)
            } while (source.metadata?.getString(AppleMusicLyricsWriter.LINE) != line && android.os.SystemClock.elapsedRealtime() < deadline)
            assertEquals(line, source.metadata?.getString(AppleMusicLyricsWriter.LINE))
            assertEquals(raw, source.metadata?.getString(AppleMusicLyricsWriter.WHOLE))
            assertEquals(track.title, source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
            assertEquals(track.artist, source.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST))
            assertEquals("Apple Music queue identity must be preserved", originalQueueId, source.metadata?.getLong(queueIdKey))
        }
        try {
            publish("IPC first line")
            publish("IPC second line")
        } finally {
            instrumentation.runOnMainSync { writer.detach() }
            Thread.sleep(750)
        }
        assertEquals(originalWhole, source.metadata?.getString(AppleMusicLyricsWriter.WHOLE))
        assertEquals(originalLine, source.metadata?.getString(AppleMusicLyricsWriter.LINE))
        assertEquals(track.title, source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
        instrumentation.sendStatus(0, android.os.Bundle().apply {
            putString("stream", "Two lyric-line updates and restoration passed; title and artist unchanged.\n")
        })
    }
}
