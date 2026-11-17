package com.ryan1202.lyricsbridge

import org.junit.Assert.*
import org.junit.Test

class SongMatcherTest {
    @Test fun recognizesExactWeekndRecordings() {
        val night = TrackInfo("night", "In The Night", "The Weeknd", "Beauty Behind the Madness", 235_653)
        val tears = TrackInfo("tears", "Save Your Tears", "The Weeknd", "After Hours", 215_626)
        assertTrue(SongMatcher.matches(night, candidate(title = "In the Night", artist = "The Weeknd", duration = 236_000)))
        assertTrue(SongMatcher.matches(tears, candidate(title = "Save Your Tears", artist = "The Weeknd", duration = 216_000)))
        assertFalse(SongMatcher.matches(tears, candidate(title = "Save Your Tears", artist = "The Weeknd", duration = 212_000)))
        assertFalse(SongMatcher.matches(tears, candidate(title = "Save Your Tears", artist = "The Weeknd", duration = 221_000)))
    }
    @Test fun roundedDurationDoesNotRestartLookupButTrackTransitionDoes() {
        assertFalse(track.copy(durationMs = track.durationMs + 102).needsLyricsLookup(track))
        assertTrue(track.copy(id = "next", title = "Next Song").needsLyricsLookup(track))
    }
    @Test fun recognizesSlashSeparatedLrclibArtists() {
        val song = track.copy(title = "下一站", artist = "HOYO-MiX")
        assertEquals(1, SongMatcher.guesses(song, listOf(candidate(title = "下一站 Beyond This Station", artist = "小林未郁/HOYO-MiX"))).size)
    }
    @Test fun ranksQqOriginalAboveItsInstrumentalReleases() {
        val original = track.copy(title = "无中生有", artist = "HOYO-MiX & MIREI", durationMs = 217_059)
        val vocal = candidate(title = "无中生有 Ex Nihilo", duration = 217_000)
            .copy(id = "0010Eg5d0I2o2b", artists = listOf("HOYO-MiX", "當山みれい"))
        val backing = vocal.copy(id = "001Joe7R2CeUGT", title = "无中生有 Ex Nihilo (伴奏)", artists = listOf("HOYO-MiX"))
        val harmony = backing.copy(id = "003DmpEv0ZHHnn", title = "无中生有 Ex Nihilo (和声伴奏)")
        val arrangement = vocal.copy(id = "arrangement", title = "无中生有（交响改编）", artists = listOf("水依竹音Suzy"))
        assertEquals(listOf(vocal, backing, harmony), SongMatcher.guesses(original, listOf(harmony, arrangement, backing, vocal)))
    }
    @Test fun excludesDifferentArtistsEvenWhenTitleAndDurationMatch() {
        val original = track.copy(title = "无中生有", artist = "HOYO-MIX & MIREI")
        val arrangement = candidate(title = "无中生有（交响改编）", artist = "水依竹音Suzy")
        assertTrue(SongMatcher.guesses(original, listOf(arrangement)).isEmpty())
        assertTrue(SongMatcher.guesses(original, listOf(arrangement.copy(title = "无中生有"))).isEmpty())
    }
    @Test fun acceptsOneMatchingArtistFromCollaborations() {
        val original = track.copy(title = "无中生有", artist = "HOYO-MIX & MIREI")
        val matched = candidate(title = "无中生有", artist = "MIREI")
        assertEquals(listOf(matched), SongMatcher.guesses(original, listOf(matched)))
    }
    @Test fun ignoresFeaturedArtistsOnlyWhenSearchingAndComparingTitles() {
        assertEquals("下一站", SongMatcher.searchTitle("下一站 （feat. 小林未郁）"))
        assertEquals("Yellow", SongMatcher.searchTitle("Yellow (Featuring Guest)"))
        assertEquals("Yellow", SongMatcher.searchTitle("Yellow feat. Guest"))
        assertEquals(1.0, SongMatcher.titleSimilarity("下一站 （feat. 小林未郁）", "下一站"), 0.0)
        assertEquals("Yellow (Live)", SongMatcher.searchTitle("Yellow (Live)"))
    }
    @Test fun guessesOnlyWithinOneSecondAndRanksByTitle() {
        val close = candidate(title = "Yelow", duration = 267_000).copy(id = "close")
        val unrelated = candidate(title = "Blue", duration = 266_000).copy(id = "other")
        val tooLong = candidate(title = "Yellow", duration = 267_001).copy(id = "outside")
        assertEquals(listOf("close", "other"), SongMatcher.guesses(track, listOf(unrelated, tooLong, close)).map { it.id })
    }
    @Test fun doesNotGuessWhenDurationIsUnknown() {
        assertTrue(SongMatcher.guesses(track.copy(durationMs = 0), listOf(candidate())).isEmpty())
        assertTrue(SongMatcher.guesses(track, listOf(candidate(duration = 0))).isEmpty())
    }
    private val track = TrackInfo("apple-1", "Yellow", "Coldplay", "Parachutes", 266_000)
    private fun candidate(title: String = "Yellow", artist: String = "Coldplay", duration: Long = 266_000, album: String = "Parachutes") =
        LyricCandidate("1", title, listOf(artist), album, duration)
    @Test fun excludesCoversLiveVersionsAndDifferentEdits() {
        assertFalse(SongMatcher.matches(track, candidate(artist = "Other Singer")))
        assertFalse(SongMatcher.matches(track, candidate(title = "Yellow (Live)")))
        assertFalse(SongMatcher.matches(track, candidate(duration = 220_000)))
    }
    @Test fun acceptsPunctuationAndMultipleArtists() {
        assertTrue(SongMatcher.matches(track, candidate(title = "YELLOW!", artist = "Coldplay & Guest", duration = 267_000)))
    }
    @Test fun prefersMatchingAlbumBeforeOtherReleases() {
        val other = candidate(album = "Compilation").copy(id = "other")
        val exact = candidate(duration = 267_000).copy(id = "exact")
        assertEquals("exact", SongMatcher.select(track, listOf(other, exact)).first().id)
    }
}
