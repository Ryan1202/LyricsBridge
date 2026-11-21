package com.ryan1202.lyricsbridge

import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class IslandLyricsFlowTest {
    @Test fun togglesPlatformLyricsAndFollowsNaturalAdvanceWithoutChangingNativeTitles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun status(message: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$message\n") })
        fun await(message: String, timeout: Long = 10_000, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeout
            while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
            assertTrue("$message; ${BridgeStatus.summary}", condition())
        }
        val source = context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaAccessService::class.java))
            .first { it.packageName == "com.apple.android.music" }
        status("Connected to ${source.packageName}")
        val enabled = SyncPreference.enabled(context)
        val islandEnabled = SyncPreference.islandLyricsEnabled(context)
        val running = BridgeStatus.ui.value.running
        val wasPlaying = source.playbackState?.state == PlaybackState.STATE_PLAYING
        val originalPosition = source.playbackState?.position ?: 0
        val originalId = source.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        var observer: MediaController? = null
        var advanced = false
        try {
            SyncPreference.setEnabled(context, false)
            SyncPreference.setIslandLyricsEnabled(context, false)
            context.stopService(Intent(context, BridgeService::class.java))
            Thread.sleep(1_000)
            source.transportControls.pause()
            source.transportControls.skipToNext()
            await("Refresh the native media item") { source.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) != originalId }
            source.transportControls.skipToPrevious()
            await("Return to the current song") { source.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) == originalId }
            source.transportControls.pause()
            val token = SessionToken.createSessionToken(context, source.sessionToken).get(10, TimeUnit.SECONDS)
            lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
            instrumentation.runOnMainSync { future = MediaController.Builder(context, token).buildAsync() }
            val native = future.get(15, TimeUnit.SECONDS)
            observer = native
            var originalTitle = ""
            instrumentation.runOnMainSync { originalTitle = native.currentMediaItem!!.mediaMetadata.title.toString() }
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SyncPreference.setEnabled(context, true)
            context.startForegroundService(Intent(context, BridgeService::class.java).setAction("follow"))
            await("Fetch the current song's lyrics", 60_000) {
                BridgeStatus.ui.value.let { it.song?.id == originalId && !it.fetching && it.provider.isNotBlank() }
            }
            await("Publish the matched lyric timeline") {
                source.metadata?.getString(AppleMusicLyricsWriter.WHOLE)?.let { LrcTimeline(it).lines.size > 2 } == true
            }
            val raw = source.metadata!!.getString(AppleMusicLyricsWriter.WHOLE)!!
            val cues = LrcTimeline(raw).lines.filter { it.timeMs > 2_000 && it.text.isNotBlank() }
            val pair = cues.zipWithNext().first { (a, b) -> a.text != b.text && b.timeMs - a.timeMs in 500..10_000 }
            status("Testing lyric boundaries for $originalTitle: ${pair.first.timeMs} -> ${pair.second.timeMs}")
            val queueKey = "com.apple.android.music.playback.metadata.ITEM_QUEUE_ID"
            val queueId = source.metadata!!.getLong(queueKey)
            source.transportControls.seekTo(pair.first.timeMs + 100)
            await("Publish the first cue") { source.metadata?.getString(AppleMusicLyricsWriter.LINE) == pair.first.text }
            assertEquals(originalTitle, source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
            fun toggle(value: Boolean) {
                SyncPreference.setIslandLyricsEnabled(context, value)
                context.startService(Intent(context, BridgeService::class.java).setAction("island"))
            }
            toggle(true)
            await("Enable island lyrics while paused") {
                source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) == pair.first.text
            }
            instrumentation.runOnMainSync {
                assertEquals(originalTitle, native.currentMediaItem?.mediaMetadata?.title?.toString())
                assertEquals(originalTitle, native.mediaMetadata.title?.toString())
            }
            assertEquals(queueId, source.metadata?.getLong(queueKey))
            assertEquals(originalTitle, BridgeStatus.ui.value.song?.title)
            assertFalse(BridgeStatus.ui.value.fetching)
            source.transportControls.play()
            await("Update the platform title at the next lyric boundary", 15_000) {
                source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) == pair.second.text &&
                    source.metadata?.getString(AppleMusicLyricsWriter.LINE) == pair.second.text
            }
            source.transportControls.pause()
            assertEquals(originalTitle, BridgeStatus.ui.value.song?.title)
            status("Consecutive lyric updates preserve native title and queue ID: $originalTitle")
            toggle(false)
            await("Disabling the switch restores the song title") {
                source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) == originalTitle
            }
            assertTrue(source.metadata?.getString(AppleMusicLyricsWriter.WHOLE).orEmpty().isNotBlank())
            toggle(true)
            await("Re-enable the current lyric") {
                source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) == source.metadata?.getString(AppleMusicLyricsWriter.LINE)
            }
            val before = BridgeStatus.ui.value.song!!
            val nextTitle = source.queue?.getOrNull(1)?.description?.title?.toString()
            assertNotNull("A following queued song is required", nextTitle)
            source.transportControls.seekTo(before.durationMs - 600)
            source.transportControls.play()
            await("Follow natural song advance with island lyrics enabled", 60_000) {
                BridgeStatus.ui.value.let {
                    it.song?.id != before.id && it.song?.title == nextTitle && !it.fetching && it.provider.isNotBlank()
                }
            }
            advanced = true
            val next = BridgeStatus.ui.value.song!!
            instrumentation.runOnMainSync {
                assertEquals(next.title, native.currentMediaItem?.mediaMetadata?.title?.toString())
            }
            assertEquals(nextTitle, next.title)
            await("Publish the next song's lyrics") {
                source.metadata?.getString(AppleMusicLyricsWriter.WHOLE).orEmpty().isNotBlank() &&
                    source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ==
                    source.metadata?.getString(AppleMusicLyricsWriter.LINE)?.takeIf { it.isNotBlank() }
            }
            status("Natural advance preserved the original queued song: ${next.title}")
            context.stopService(Intent(context, BridgeService::class.java))
            await("Stopping sync restores the current song title") {
                source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) == next.title
            }
            status("Switch on/off and stopping sync restore titles; native titles and lyric matching remain correct")
        } finally {
            SyncPreference.setEnabled(context, false)
            context.stopService(Intent(context, BridgeService::class.java))
            Thread.sleep(750)
            source.transportControls.pause()
            if (advanced) {
                source.transportControls.skipToPrevious()
                await("Restore the original queued song") { source.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) == originalId }
            }
            source.transportControls.seekTo(originalPosition)
            if (wasPlaying) source.transportControls.play() else source.transportControls.pause()
            observer?.let { connected -> instrumentation.runOnMainSync { connected.release() } }
            SyncPreference.setIslandLyricsEnabled(context, islandEnabled)
            SyncPreference.setEnabled(context, enabled)
            if (enabled && running) context.startForegroundService(Intent(context, BridgeService::class.java).setAction("auto"))
        }
    }
}
