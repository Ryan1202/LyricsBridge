package com.ryan1202.lyricsbridge

import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BackgroundLyricsFlowTest {
    @Test fun naturalAdvancePublishesNextLyricsAndReopeningKeepsTheCurrentSong() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun status(message: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$message\n") })
        fun await(message: String, timeoutMs: Long, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
            assertTrue("$message; ${BridgeStatus.summary}", condition())
        }
        val source = context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaAccessService::class.java))
            .first { it.packageName == "com.apple.android.music" }
        val observed = AtomicReference(source.metadata)
        val wasPlaying = source.playbackState?.state == PlaybackState.STATE_PLAYING
        val enabled = SyncPreference.enabled(context)
        val islandEnabled = SyncPreference.islandLyricsEnabled(context)
        val expectedId = AtomicReference<String?>(null)
        val overwritten = AtomicReference<MediaMetadata?>(null)
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                observed.set(metadata)
                expectedId.get()?.let { id ->
                    if (metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) != id) overwritten.set(metadata)
                }
            }
        }
        source.registerCallback(callback, Handler(Looper.getMainLooper()))
        try {
            val initialId = observed.get()?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            SyncPreference.setEnabled(context, true)
            SyncPreference.setIslandLyricsEnabled(context, false)
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            context.startForegroundService(Intent(context, BridgeService::class.java).setAction("follow"))
            await("Initial song lyrics must be ready", 45_000) {
                val ui = BridgeStatus.ui.value
                ui.song?.id == initialId && !ui.fetching && ui.provider.isNotBlank()
            }
            val before = BridgeStatus.ui.value.song!!
            val queue = source.queue.orEmpty()
            val currentIndex = queue.indexOfFirst { it.queueId == source.playbackState?.activeQueueItemId }
            assertTrue("The current song must be present in Apple Music's queue", currentIndex >= 0)
            val expectedNext = queue.getOrNull(currentIndex + 1)?.description?.title?.toString()
            assertNotNull("A following song is needed in the original Apple Music queue", expectedNext)
            assertNotEquals(before.title, expectedNext)
            assertTrue(before.durationMs > 5_000)
            val previousRaw = observed.get()?.getString(AppleMusicLyricsWriter.WHOLE)
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            status("On Home; awaiting natural advance: ${before.title} -> $expectedNext")
            source.transportControls.seekTo(before.durationMs - 1_000)
            source.transportControls.play()
            await("Background natural advance must publish the next song's lyrics", 45_000) {
                val ui = BridgeStatus.ui.value
                val raw = observed.get()?.getString(AppleMusicLyricsWriter.WHOLE).orEmpty()
                ui.song?.title == expectedNext && !ui.fetching && ui.provider.isNotBlank() &&
                    raw.isNotBlank() && raw != previousRaw &&
                    observed.get()?.getString(MediaMetadata.METADATA_KEY_TITLE) == expectedNext
            }
            val next = BridgeStatus.ui.value.song!!
            val publishedRaw = observed.get()!!.getString(AppleMusicLyricsWriter.WHOLE)!!
            val cue = LrcTimeline(publishedRaw).lines.first { it.timeMs > 2_000 && it.text.isNotBlank() }
            source.transportControls.seekTo(cue.timeMs - 400)
            await("Current lyric line must update while the app remains in the background", 10_000) {
                observed.get()?.getString(AppleMusicLyricsWriter.LINE) == cue.text
            }
            assertEquals(expectedNext, observed.get()?.getString(MediaMetadata.METADATA_KEY_TITLE))
            assertEquals(next.artist, observed.get()?.getString(MediaMetadata.METADATA_KEY_ARTIST))
            assertEquals(next.identity, BridgeStatus.ui.value.song?.identity)
            source.transportControls.pause()
            expectedId.set(next.id)
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            Thread.sleep(2_000)
            assertNull("Opening the app must not write the previous song back into the session", overwritten.get())
            assertEquals(next.identity, BridgeStatus.ui.value.song?.identity)
            assertEquals(next.id, source.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
            assertEquals(next.title, source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
            status("Background natural advance, lyric-line publication and reopening preserve the current song: ${next.title}")
        } finally {
            expectedId.set(null)
            if (wasPlaying) source.transportControls.play() else source.transportControls.pause()
            source.unregisterCallback(callback)
            SyncPreference.setIslandLyricsEnabled(context, islandEnabled)
            SyncPreference.setEnabled(context, enabled)
            if (!enabled) context.stopService(Intent(context, BridgeService::class.java))
        }
    }
}
