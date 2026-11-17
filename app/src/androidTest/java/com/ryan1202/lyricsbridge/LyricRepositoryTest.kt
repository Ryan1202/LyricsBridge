package com.ryan1202.lyricsbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LyricRepositoryTest {
    private val track = TrackInfo("night", "In The Night", "The Weeknd", "Beauty Behind the Madness", 235_653)
    private val raw = "[00:00.00]First\n[00:10.00]Second"
    private fun cache(name: String) = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "$name.json").apply { delete() }
    private fun lrclib(instrumental: Boolean = false) = JSONArray().put(JSONObject()
        .put("id", 1).put("trackName", track.title).put("artistName", track.artist).put("albumName", track.album)
        .put("duration", 236).put("instrumental", instrumental).put("syncedLyrics", if (instrumental) JSONObject.NULL else raw)).toString()
    private fun qqSearch() = JSONObject().put("code", 0).put("req", JSONObject().put("code", 0)
        .put("data", JSONObject().put("body", JSONObject().put("song", JSONObject().put("list", JSONArray().put(JSONObject()
            .put("mid", "qq-night").put("title", track.title).put("interval", 236)
            .put("album", JSONObject().put("name", track.album))
            .put("singer", JSONArray().put(JSONObject().put("name", track.artist))))))))).toString()
    private val noNeteaseSongs = "{\"code\":200,\"result\":{\"songs\":[]}}"
    private val noQqSongs = "{\"code\":0,\"req\":{\"code\":0,\"data\":{\"body\":{\"song\":{\"list\":[]}}}}}"
    private class Response(url: URL, private val read: () -> String, private val closed: () -> Unit = {}) : HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() { closed() }
        override fun usingProxy() = false
        override fun getResponseCode() = HTTP_OK
        override fun getInputStream() = ByteArrayInputStream(read().toByteArray(Charsets.UTF_8))
    }
    private fun blocked(latch: CountDownLatch): String {
        latch.countDown()
        try { CountDownLatch(1).await(10, TimeUnit.SECONDS) }
        catch (_: InterruptedException) { throw InterruptedIOException("cancelled") }
        return "[]"
    }
    @Test fun firstExactQqResultDoesNotWaitForSlowLrclib() {
        val started = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val file = cache("qq-race")
        try {
            val result = LyricRepository(file) { url -> Response(url, {
                when {
                    url.host == "lrclib.net" -> blocked(started)
                    url.host == "music.163.com" -> noNeteaseSongs
                    url.host == "u.y.qq.com" -> { assertTrue(started.await(3, TimeUnit.SECONDS)); qqSearch() }
                    else -> JSONObject().put("code", 0).put("lyric", raw).toString()
                }
            }, { if (url.host == "lrclib.net") closed.countDown() }) }.find(track)
            assertEquals("QQ音乐", result.source)
            assertFalse(result.guessed)
            assertTrue(closed.await(3, TimeUnit.SECONDS))
        } finally { file.delete() }
    }
    @Test fun firstExactLrclibResultCancelsSlowQq() {
        val started = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val file = cache("lrclib-race")
        try {
            val result = LyricRepository(file) { url -> Response(url, {
                when (url.host) {
                    "lrclib.net" -> { assertTrue(started.await(3, TimeUnit.SECONDS)); lrclib() }
                    "u.y.qq.com" -> blocked(started)
                    else -> noNeteaseSongs
                }
            }, { if (url.host == "u.y.qq.com") closed.countDown() }) }.find(track)
            assertEquals("LRCLIB", result.source)
            assertFalse(result.guessed)
            assertTrue(closed.await(3, TimeUnit.SECONDS))
        } finally { file.delete() }
    }
    @Test fun noLyricsFlagDoesNotBecomeInstrumental() {
        val file = cache("no-lyrics-flag")
        val neteaseSearch = JSONObject().put("code", 200).put("result", JSONObject().put("songs", JSONArray().put(JSONObject()
            .put("id", 1).put("name", track.title).put("duration", 236_000).put("album", JSONObject().put("name", track.album))
            .put("artists", JSONArray().put(JSONObject().put("name", track.artist)))))).toString()
        try {
            val repository = LyricRepository(file) { url -> Response(url, {
                when {
                    url.host == "lrclib.net" -> "[]"
                    url.host == "u.y.qq.com" -> noQqSongs
                    url.path.endsWith("lyric") -> "{\"code\":200,\"nolyric\":true}"
                    else -> neteaseSearch
                }
            }) }
            try { repository.find(track); fail("No-lyrics status must not produce an instrumental result") }
            catch (expected: java.io.IOException) { assertTrue(expected.message.orEmpty().contains("未找到")) }
        } finally { file.delete() }
    }
    @Test fun appleCatalogCorrectsArtistAndReplacesStaleInstrumentalCache() {
        val song = TrackInfo("1499386369", "Save Your Tears", "Abel Tesfaye", "After Hours", 215_000)
        val file = cache("catalog-artist")
        file.writeText(JSONObject().put(song.key, JSONObject().put("raw", "").put("source", "LRCLIB")
            .put("match", "${song.title} · Abel Tesfaye").put("instrumental", true).put("guessed", false)
            .put("matchedTitle", song.title).put("matchedArtists", JSONArray().put(song.artist)).put("matchedDuration", 215_000)).toString())
        val requests = java.util.concurrent.atomic.AtomicInteger()
        try {
            val repository = LyricRepository(file) { url -> Response(url, {
                requests.incrementAndGet()
                when (url.host) {
                    "itunes.apple.com" -> JSONObject().put("results", JSONArray().put(JSONObject().put("trackId", 1499386369)
                        .put("trackName", song.title).put("artistName", track.artist).put("collectionName", song.album))).toString()
                    "lrclib.net" -> if (android.net.Uri.parse(url.toString()).getQueryParameter("artist_name") == song.artist)
                        JSONArray().put(JSONObject().put("id", 2).put("trackName", song.title).put("artistName", song.artist)
                            .put("duration", 215).put("instrumental", true)).toString() else JSONArray().put(JSONObject()
                                .put("id", 1).put("trackName", song.title).put("artistName", track.artist).put("albumName", song.album)
                                .put("duration", 216).put("instrumental", false).put("syncedLyrics", raw)).toString()
                    "u.y.qq.com" -> noQqSongs
                    else -> noNeteaseSongs
                }
            }) }
            val result = repository.find(song)
            assertEquals("LRCLIB", result.source)
            assertFalse(result.instrumental)
            assertFalse(result.guessed)
            assertTrue(result.match.contains("The Weeknd"))
            val count = requests.get()
            assertEquals(result, repository.find(song))
            assertEquals(count, requests.get())
        } finally { file.delete() }
    }
    @Test fun actualInstrumentalTracksRemainMatchable() {
        val file = cache("instrumental-track")
        try {
            val result = LyricRepository(file) { url -> Response(url, {
                when (url.host) { "lrclib.net" -> lrclib(instrumental = true); "u.y.qq.com" -> noQqSongs; else -> noNeteaseSongs }
            }) }.find(track)
            assertTrue(result.instrumental)
            assertFalse(result.guessed)
        } finally { file.delete() }
    }
    @Test fun liveWeekndSongsHaveTimedVocalLyrics() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.startActivity(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        for (song in listOf(track, track.copy(id = "1440826580", artist = "Abel Tesfaye"),
            TrackInfo("1499386369", "Save Your Tears", "Abel Tesfaye", "After Hours", 215_000))) {
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Looking up ${song.title}; artist=${song.artist}\n") })
            val file = cache("live-${song.id}")
            try {
                val result = LyricRepository(file).find(song, refresh = true)
                assertFalse("${song.title}: ${result.match}", result.instrumental)
                assertFalse("${song.title}: ${result.match}", result.guessed)
                assertTrue(LrcTimeline(result.raw).lines.size > 20)
                instrumentation.sendStatus(0, android.os.Bundle().apply {
                    putString("stream", "${song.title}: ${result.source}; ${result.match}; timed lines=${LrcTimeline(result.raw).lines.size}; exact vocal match.\n")
                })
            } finally { file.delete() }
        }
    }
}
