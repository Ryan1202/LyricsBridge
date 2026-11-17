package com.ryan1202.lyricsbridge

import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoLyricsFlowTest {
    @Test fun currentSongIsMatchedAndPublishedWithoutChangingTitle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val source = manager.getActiveSessions(ComponentName(context, MediaAccessService::class.java))
            .first { it.packageName == "com.apple.android.music" }
        val originalTitle = source.metadata!!.getString(MediaMetadata.METADATA_KEY_TITLE)
        context.startForegroundService(Intent(context, BridgeService::class.java).setAction("retry"))
        try {
            val deadline = android.os.SystemClock.elapsedRealtime() + 60_000
            var published = false
            while (!published && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(250)
                published = source.metadata?.getString(AppleMusicLyricsWriter.WHOLE)?.let { LrcTimeline(it).lines.isNotEmpty() } == true
                if (BridgeStatus.summary.contains("获取失败")) break
            }
            assertTrue(BridgeStatus.summary, published)
            assertEquals(originalTitle, source.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
            assertEquals(source.sessionToken, manager.getActiveSessions(ComponentName(context, MediaAccessService::class.java))
                .first { it.packageName == "com.apple.android.music" }.sessionToken)
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("stream", "Automatic lyric lookup, matching and original-session publishing passed; title unchanged.\n${BridgeStatus.summary}\n")
            })
        } finally {
            context.stopService(Intent(context, BridgeService::class.java))
            Thread.sleep(750)
        }
    }
}
