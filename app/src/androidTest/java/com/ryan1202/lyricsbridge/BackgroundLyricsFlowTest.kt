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
    @Test fun naturalAdvancePublishesNextLyricsAndUpdatesLinesWhileOnHome() {
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
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { observed.set(metadata) }
        }
        source.registerCallback(callback, Handler(Looper.getMainLooper()))
        try {
            val initial = TrackInfo.from(observed.get())!!
            source.transportControls.skipToNext()
            await("Refresh Apple Music native metadata before writing", 10_000) {
                TrackInfo.from(observed.get())?.let { it.identity != initial.identity } == true
            }
            val before = TrackInfo.from(observed.get())!!
            val expectedNext = source.queue?.getOrNull(1)?.description?.title?.toString()
            assertNotNull("A following song is needed in the original Apple Music queue", expectedNext)
            assertNotEquals(before.title, expectedNext)
            assertTrue(before.durationMs > 5_000)
            SyncPreference.setEnabled(context, true)
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            context.startForegroundService(Intent(context, BridgeService::class.java).setAction("follow"))
            await("Initial song lyrics must be ready", 45_000) {
                val ui = BridgeStatus.ui.value
                ui.song?.identity == before.identity && !ui.fetching && ui.provider.isNotBlank()
            }
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
            status("Background natural advance and timed lyric-line publication passed: ${next.title}; provider=${BridgeStatus.ui.value.provider}")
        } finally {
            if (!wasPlaying) source.transportControls.pause()
            source.unregisterCallback(callback)
            SyncPreference.setEnabled(context, enabled)
            if (!enabled) context.stopService(Intent(context, BridgeService::class.java))
        }
    }
}
