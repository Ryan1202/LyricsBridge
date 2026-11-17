package com.ryan1202.lyricsbridge

import org.junit.Assert.*
import org.junit.Test

class LyricPublicationTest {
    @Test fun matchingTextIsPublishedAsAvailableLyrics() {
        assertEquals(0L, LyricPublication.matching("Matching lyrics").status)
        assertEquals("Matching lyrics", LyricPublication.matching("Matching lyrics").timeline.at(0))
    }
    @Test fun guessNoticePrecedesLyricsAndPreservesTiming() {
        val result = LyricResult("[00:12.30]First\n[00:16.80]Second", "LRCLIB", "Song", guessed = true)
        val publication = LyricPublication.from(result)
        assertEquals(LyricPublication.GUESS_NOTICE, publication.timeline.at(0))
        assertEquals("First", publication.timeline.at(12_300))
        assertEquals("Second", publication.timeline.at(16_800))
        assertTrue(publication.raw.startsWith("[00:00.000]${LyricPublication.GUESS_NOTICE}"))
    }
    @Test fun zeroTimeFirstLineKeepsGuessNoticeVisible() {
        val result = LyricResult("[00:00.00]First\n[00:05.00]Second", "QQ音乐", "Song", guessed = true)
        val publication = LyricPublication.from(result)
        assertEquals("${LyricPublication.GUESS_NOTICE} First", publication.timeline.at(0))
        assertEquals("Second", publication.timeline.at(5_000))
    }
    @Test fun exactLyricsAndInstrumentalStatusArePreserved() {
        val raw = "[00:01.00]First"
        assertEquals(raw, LyricPublication.from(LyricResult(raw, "LRCLIB", "Song")).raw)
        assertEquals(1L, LyricPublication.from(LyricResult("", "LRCLIB", "Song", instrumental = true)).status)
    }
    @Test fun translatedNoticesPreserveOriginalLyricsAndTiming() {
        val result = LyricResult("[00:12.30]原文歌词\n[00:16.80]Second", "LRCLIB", "Song", guessed = true)
        val publication = LyricPublication.from(result, "Approximate match", "Instrumental")
        assertEquals("Approximate match", publication.timeline.at(0))
        assertEquals("原文歌词", publication.timeline.at(12_300))
        assertEquals("Second", publication.timeline.at(16_800))
        val instrumental = LyricPublication.from(result.copy(raw = "", instrumental = true), "Approximate match", "Instrumental")
        assertEquals("Approximate match Instrumental", instrumental.timeline.at(0))
        assertEquals(1L, instrumental.status)
    }
    @Test fun guessedInstrumentalAlsoIncludesNotice() {
        val publication = LyricPublication.from(LyricResult("", "LRCLIB", "Song", instrumental = true, guessed = true))
        assertEquals("${LyricPublication.GUESS_NOTICE} 纯音乐", publication.timeline.at(0))
        assertEquals(1L, publication.status)
    }
}
