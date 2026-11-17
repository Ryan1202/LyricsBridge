package com.ryan1202.lyricsbridge

import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoAdvanceTest {
    @Test fun followsAutomaticTransitionWithoutRestoringPreviousSong() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaAccessService::class.java))
            .first { it.packageName == "com.apple.android.music" }
        val staleIdentity = TrackInfo.from(source.metadata)?.identity
        source.transportControls.skipToNext()
        val refreshDeadline = SystemClock.elapsedRealtime() + 8_000
        while (TrackInfo.from(source.metadata)?.identity == staleIdentity && SystemClock.elapsedRealtime() < refreshDeadline) Thread.sleep(100)
        val before = TrackInfo.from(source.metadata)!!
        assertNotEquals("Refresh Apple Music's current song before starting the bridge", staleIdentity, before.identity)
        val expectedNext = source.queue?.getOrNull(1)?.description
        assertNotNull("A following song is required in Apple Music's original queue", expectedNext)
        val expectedTitle = expectedNext!!.title?.toString()
        assertTrue("The current song needs a known duration", before.durationMs > 5_000)
        assertTrue(source.playbackState!!.actions and PlaybackState.ACTION_SEEK_TO != 0L)
        context.startForegroundService(Intent(context, BridgeService::class.java).setAction("follow"))
        try {
            val readyDeadline = SystemClock.elapsedRealtime() + 15_000
            while ((BridgeStatus.ui.value.song?.identity != before.identity || BridgeStatus.ui.value.fetching) &&
                SystemClock.elapsedRealtime() < readyDeadline) Thread.sleep(100)
            source.transportControls.seekTo(before.durationMs - 600)
            source.transportControls.play()
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while ((BridgeStatus.ui.value.song == null || BridgeStatus.ui.value.song?.identity == before.identity) &&
                SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
            val next = BridgeStatus.ui.value.song
            assertNotNull("Apple Music must advance to another queued song", next)
            assertNotEquals("The UI must stop showing the previous song", before.identity, next!!.identity)
            assertEquals("The UI must show the next song from the original queue", expectedTitle, next.title)
            Thread.sleep(2_000)
            assertEquals(next.identity, BridgeStatus.ui.value.song?.identity)
            assertEquals(next.title, source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("stream", "Automatic transition followed the original queued song; UI and session remained correct.\n")
            })
        } finally {
            context.stopService(Intent(context, BridgeService::class.java))
            Thread.sleep(750)
        }
    }
}
