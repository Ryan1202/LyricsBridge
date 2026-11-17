package com.ryan1202.lyricsbridge

import android.media.MediaMetadata

data class TrackInfo(val id: String, val title: String, val artist: String, val album: String, val durationMs: Long) {
    val identity get() = listOf(id, title, artist, album).joinToString("\n")
    val key get() = listOf(id, title, artist, album, durationMs.toString()).joinToString("\n")
    fun needsLyricsLookup(previous: TrackInfo?) = identity != previous?.identity ||
        kotlin.math.abs(durationMs - (previous?.durationMs ?: 0)) > 1_000
    companion object {
        fun from(metadata: MediaMetadata?): TrackInfo? {
            metadata ?: return null
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            if (title.isBlank() || artist.isBlank()) return null
            return TrackInfo(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty(), title, artist,
                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(), metadata.getLong(MediaMetadata.METADATA_KEY_DURATION))
        }
    }
}

data class LyricCandidate(val id: String, val title: String, val artists: List<String>, val album: String, val durationMs: Long)

object SongMatcher {
    fun searchTitle(title: String): String = title
        .replace(Regex("[（(]\\s*(?:feat\\.?|featuring|ft\\.?)\\s+[^）)]*[）)]", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+(?:feat\\.?|featuring|ft\\.?)\\s+.*$", RegexOption.IGNORE_CASE), "")
        .trim()
    fun titleSimilarity(first: String, second: String): Double {
        val a = normalize(searchTitle(first))
        val b = normalize(searchTitle(second))
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1,
                previous[j] + if (a[i] == b[j]) 0 else 1)
            previous = current
        }
        return 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length)
    }
    fun guesses(track: TrackInfo, candidates: List<LyricCandidate>): List<LyricCandidate> {
        if (track.durationMs <= 0) return emptyList()
        return candidates.filter { it.durationMs > 0 && kotlin.math.abs(it.durationMs - track.durationMs) <= 1_000 &&
            sharesArtist(track.artist, it.artists) }
            .sortedWith(compareByDescending<LyricCandidate> { titleSimilarity(track.title, it.title) }
                .thenBy { kotlin.math.abs(it.durationMs - track.durationMs) })
    }
    fun normalize(value: String) = value.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
    private fun artists(value: String) = value.split(Regex("\\s*(?:、|,|，|&|;|；| feat\\.? | featuring |/|\\s+和\\s+)\\s*", RegexOption.IGNORE_CASE))
        .map(::normalize).filter { it.isNotEmpty() }
    private fun sharesArtist(expected: String, actual: List<String>): Boolean {
        val names = artists(expected)
        return actual.flatMap(::artists).any { it in names }
    }
    fun matches(track: TrackInfo, candidate: LyricCandidate): Boolean {
        if (normalize(track.title) != normalize(candidate.title)) return false
        if (!sharesArtist(track.artist, candidate.artists)) return false
        return track.durationMs <= 0 || candidate.durationMs <= 0 || kotlin.math.abs(track.durationMs - candidate.durationMs) <= 1_000
    }
    fun select(track: TrackInfo, candidates: List<LyricCandidate>): List<LyricCandidate> = candidates.filter { matches(track, it) }
        .sortedWith(compareBy<LyricCandidate> { normalize(it.album) != normalize(track.album) }
            .thenBy { if (track.durationMs > 0 && it.durationMs > 0) kotlin.math.abs(track.durationMs - it.durationMs) else Long.MAX_VALUE })
}
