package com.ryan1202.lyricsbridge

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

data class LyricResult(val raw: String, val source: String, val match: String, val instrumental: Boolean = false, val guessed: Boolean = false)

class LyricRepository(private val cacheFile: File,
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    private data class Entry(val song: LyricCandidate, val source: String, val embedded: LyricResult? = null)
    private data class Match(val entry: Entry, val lyric: LyricResult, val lookupTrack: TrackInfo)
    private data class Batch(val entries: List<Entry>, val exact: Match?)
    private val connections = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    fun find(track: TrackInfo, refresh: Boolean = false): LyricResult {
        val cache = readCache()
        if (!refresh) cache.optJSONObject(track.key)?.let {
            val saved = LyricResult(it.getString("raw"), it.getString("source"), it.getString("match"), it.optBoolean("instrumental"), it.optBoolean("guessed"))
            val names = it.optJSONArray("matchedArtists") ?: JSONArray()
            val candidate = LyricCandidate("", it.optString("matchedTitle"),
                (0 until names.length()).map { index -> names.getString(index) }, "", it.optLong("matchedDuration"))
            val lookupTrack = track.copy(title = it.optString("lookupTitle", track.title), artist = it.optString("lookupArtist", track.artist))
            if (!(saved.instrumental && saved.source == "网易云音乐") &&
                (!saved.instrumental || it.has("lookupArtist")) &&
                (if (saved.guessed) SongMatcher.guesses(lookupTrack, listOf(candidate)).isNotEmpty()
                    else SongMatcher.matches(lookupTrack, candidate))) return saved.copy(match = matchDescription(candidate))
        }
        val errors = ConcurrentLinkedQueue<String>()
        val loaded = ConcurrentHashMap<Entry, Result<LyricResult?>>()
        fun search(source: () -> List<Entry>): List<Entry> = try { source() }
            catch (e: IOException) { errors.add(e.message.orEmpty()); emptyList() }
        fun load(entries: List<Entry>, expected: TrackInfo, guessed: Boolean = false): Match? {
            var instrumental: Match? = null
            for (entry in entries.take(3)) {
                try {
                    val lyric = loaded.computeIfAbsent(entry) { runCatching {
                        when (entry.source) {
                            "QQ音乐" -> qqLyric(entry.song)
                            "网易云音乐" -> neteaseLyric(entry.song)
                            else -> entry.embedded
                        }
                    } }.getOrThrow()
                    if (lyric != null) {
                        val match = Match(entry, lyric.copy(guessed = guessed), expected)
                        if (!lyric.instrumental) return match
                        if (instrumental == null) instrumental = match
                    }
                } catch (e: IOException) { errors.add("${entry.source}：${e.message}") }
            }
            return instrumental
        }
        val pool = Executors.newFixedThreadPool(3)
        val tasks = mutableListOf<java.util.concurrent.Future<*>>()
        fun round(expected: TrackInfo): Batch {
            val completed = ExecutorCompletionService<Batch>(pool)
            val searches = listOf({ lrclib(expected) }, { qq(expected) }, { netease(expected) }).map { provider ->
                completed.submit(java.util.concurrent.Callable {
                    val entries = search(provider)
                    val exact = SongMatcher.select(expected, entries.map { it.song })
                        .map { song -> entries.first { it.song.id == song.id } }
                    Batch(entries, load(exact, expected))
                }).also { tasks.add(it) }
            }
            val candidates = mutableListOf<Entry>()
            var winner: Match? = null
            var instrumental: Match? = null
            for (index in searches.indices) {
                val batch = try { completed.take().get() }
                catch (e: java.util.concurrent.ExecutionException) {
                    errors.add(e.cause?.message ?: "歌词来源请求失败")
                    continue
                }
                candidates.addAll(batch.entries)
                val found = batch.exact
                if (found != null) {
                    if (!found.lyric.instrumental) { winner = found; break }
                    if (instrumental == null) instrumental = found
                }
            }
            return Batch(candidates, winner ?: instrumental)
        }
        val match = try {
            val initial = round(track)
            if (initial.exact?.lyric?.instrumental == false) initial.exact else {
                val canonical = pool.submit(java.util.concurrent.Callable { appleTrack(track) }).also { tasks.add(it) }.get()
                val expected = canonical ?: track
                val batch = if (canonical != null && (canonical.title != track.title || canonical.artist != track.artist))
                    round(canonical) else initial
                batch.exact ?: run {
                    val candidates = (batch.entries + initial.entries).distinct()
                    val ranked = SongMatcher.guesses(expected, candidates.map { it.song })
                        .map { song -> candidates.first { it.song == song } }
                    pool.submit(java.util.concurrent.Callable { load(ranked, expected, guessed = true) }).also { tasks.add(it) }.get()
                }
            }
        } finally {
            tasks.forEach { it.cancel(true) }
            pool.shutdownNow()
            connections.forEach { it.disconnect() }
            connections.clear()
        }
        val found = match?.lyric ?: throw IOException(if (errors.isEmpty()) "未找到可用的匹配歌词" else errors.joinToString("；"))
        val matchedSong = match.entry.song
        cache.remove(track.key)
        cache.put(track.key, JSONObject().put("raw", found.raw).put("source", found.source)
            .put("match", found.match).put("instrumental", found.instrumental).put("guessed", found.guessed)
            .put("matchedTitle", matchedSong.title).put("matchedArtists", JSONArray(matchedSong.artists))
            .put("matchedDuration", matchedSong.durationMs)
            .put("lookupTitle", match.lookupTrack.title).put("lookupArtist", match.lookupTrack.artist))
        while (cache.length() > 100) cache.remove(cache.keys().next())
        cacheFile.writeText(cache.toString())
        return found
    }
    private fun matchDescription(candidate: LyricCandidate) =
        "${candidate.title} · ${candidate.artists.joinToString(" / ")} · ${candidate.durationMs / 1000} s"

    private fun readCache(): JSONObject = try {
        if (cacheFile.exists()) JSONObject(cacheFile.readText()) else JSONObject()
    } catch (_: org.json.JSONException) { JSONObject() }

    private fun appleTrack(track: TrackInfo): TrackInfo? {
        if (track.id.isBlank() || track.id.any { !it.isDigit() }) return null
        for (country in listOf("cn", "us")) {
            val response = try { objectRequest("https://itunes.apple.com/lookup", mapOf("id" to track.id, "country" to country)) }
                catch (_: IOException) { continue }
            val songs = response.optJSONArray("results") ?: continue
            for (index in 0 until songs.length()) {
                val song = songs.getJSONObject(index)
                if (song.optLong("trackId").toString() != track.id) continue
                val title = song.optString("trackName")
                val artist = song.optString("artistName")
                if (title.isNotBlank() && artist.isNotBlank()) return track.copy(title = title, artist = artist,
                    album = song.optString("collectionName", track.album))
            }
        }
        return null
    }

    private fun qq(track: TrackInfo): List<Entry> {
        val payload = JSONObject().put("req", JSONObject()
            .put("module", "music.search.SearchCgiService").put("method", "DoSearchForQQMusicDesktop")
            .put("param", JSONObject().put("search_type", 0)
                .put("query", "${SongMatcher.searchTitle(track.title)} ${track.artist}")
                .put("page_num", 1).put("num_per_page", 30)))
        val response = objectRequest("https://u.y.qq.com/cgi-bin/musicu.fcg", mapOf("data" to payload.toString()))
        val result = response.optJSONObject("req")
        if (response.optInt("code", -1) != 0 || result?.optInt("code", -1) != 0)
            throw IOException("QQ音乐检索失败")
        val songs = result.optJSONObject("data")?.optJSONObject("body")?.optJSONObject("song")?.optJSONArray("list")
            ?: return emptyList()
        return (0 until songs.length()).map { index ->
            val song = songs.getJSONObject(index)
            val singers = song.optJSONArray("singer") ?: JSONArray()
            Entry(LyricCandidate(song.getString("mid"), song.optString("title", song.optString("name")),
                (0 until singers.length()).map { singers.getJSONObject(it).optString("name") },
                song.optJSONObject("album")?.optString("name").orEmpty(), song.optLong("interval") * 1000), "QQ音乐")
        }
    }
    private fun qqLyric(candidate: LyricCandidate): LyricResult? {
        val data = objectRequest("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
            mapOf("songmid" to candidate.id, "format" to "json", "nobase64" to "1", "g_tk" to "5381"))
        if (data.optInt("code", -1) != 0) throw IOException("QQ音乐歌词获取失败")
        val raw = data.optString("lyric")
        if (LrcTimeline(raw).lines.none { it.text.isNotBlank() }) return null
        return LyricResult(raw, "QQ音乐", matchDescription(candidate))
    }

    private fun netease(track: TrackInfo): List<Entry> {
        val response = objectRequest("https://music.163.com/api/search/get", mapOf(
            "s" to "${SongMatcher.searchTitle(track.title)} ${track.artist}", "type" to "1", "limit" to "30", "offset" to "0"))
        if (response.optInt("code") != 200) throw IOException("检索失败（${response.optInt("code")}）")
        val songs = response.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        val candidates = (0 until songs.length()).map { index ->
            val song = songs.getJSONObject(index)
            val artists = song.optJSONArray("artists") ?: song.optJSONArray("ar") ?: JSONArray()
            LyricCandidate(song.getLong("id").toString(), song.getString("name"),
                (0 until artists.length()).map { artists.getJSONObject(it).optString("name") },
                (song.optJSONObject("album") ?: song.optJSONObject("al"))?.optString("name").orEmpty(),
                song.optLong("duration", song.optLong("dt")))
        }
        return candidates.map { Entry(it, "网易云音乐") }
    }
    private fun neteaseLyric(candidate: LyricCandidate): LyricResult? {
        val data = objectRequest("https://music.163.com/api/song/lyric", mapOf("id" to candidate.id, "lv" to "-1", "kv" to "-1", "tv" to "-1"))
        val raw = data.optJSONObject("lrc")?.optString("lyric").orEmpty()
        if (LrcTimeline(raw).lines.any { it.text.isNotBlank() }) {
            return LyricResult(raw, "网易云音乐", matchDescription(candidate))
        }
        return null
    }
    private fun lrclib(track: TrackInfo): List<Entry> {
        val rows = try {
            val byTitle = JSONArray(request("https://lrclib.net/api/search", mapOf("track_name" to SongMatcher.searchTitle(track.title), "artist_name" to track.artist)))
            if (byTitle.length() > 0) byTitle else JSONArray(request("https://lrclib.net/api/search",
                mapOf("q" to track.artist.replace(Regex("[&,，、;/；]"), " "))))
        }
        catch (e: org.json.JSONException) { throw IOException("响应格式错误", e) }
        val candidates = (0 until rows.length()).map { index ->
            val song = rows.getJSONObject(index)
            LyricCandidate(song.getLong("id").toString(), song.getString("trackName"), listOf(song.getString("artistName")),
                song.optString("albumName"), (song.optDouble("duration", 0.0) * 1000).toLong())
        }
        return candidates.map { candidate ->
            val song = (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.getLong("id").toString() == candidate.id }
            val raw = if (song.isNull("syncedLyrics")) "" else song.optString("syncedLyrics")
            val lyric = if (LrcTimeline(raw).lines.any { it.text.isNotBlank() }) LyricResult(raw, "LRCLIB", matchDescription(candidate))
                else if (song.optBoolean("instrumental")) LyricResult("", "LRCLIB", "${candidate.title} · ${candidate.artists.joinToString(" / ")}", true) else null
            Entry(candidate, "LRCLIB", lyric)
        }
    }
    private fun objectRequest(url: String, params: Map<String, String>): JSONObject = try { JSONObject(request(url, params)) }
        catch (e: org.json.JSONException) { throw IOException("响应格式错误", e) }
    private fun request(url: String, params: Map<String, String>): String {
        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("检索已取消")
        val uri = Uri.parse(url).buildUpon().apply { params.forEach { (key, value) -> appendQueryParameter(key, value) } }.build()
        val connection = openConnection(URL(uri.toString()))
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "LyricsBridge/1.0")
        if (url.contains("music.163.com")) connection.setRequestProperty("Referer", "https://music.163.com/")
        if (url.contains(".y.qq.com/")) connection.setRequestProperty("Referer", "https://y.qq.com/")
        connections.add(connection)
        try {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("检索已取消")
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connections.remove(connection); connection.disconnect() }
    }
}
