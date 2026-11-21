package com.ryan1202.lyricsbridge

import org.junit.Assert.*
import org.junit.Test

class TrackInfoTest {
    private val previous = TrackInfo("first", "First Song", "Artist", "Album", 200_000)
    private val next = previous.copy(id = "second", title = "Second Song")

    @Test fun platformTransitionTakesPrecedenceOverStaleNativeItem() {
        assertEquals(next, TrackInfo.current(previous, next, false))
        assertFalse(previous.matchesSessionTrack(next))
    }

    @Test fun nativeTransitionWaitsForPlatformConfirmation() {
        assertEquals(previous, TrackInfo.current(next, previous, false))
        assertFalse(next.matchesSessionTrack(previous))
    }

    @Test fun islandTitleUsesNativeSongTitleWithoutRestartingLookup() {
        val platform = next.copy(title = "Current lyric line", durationMs = next.durationMs + 100)
        val current = TrackInfo.current(next, platform, true)
        assertEquals(next, current)
        assertFalse(current!!.needsLyricsLookup(next))
        assertTrue(next.matchesSessionTrack(platform, true))
    }

    @Test fun islandTitleDoesNotHideANewSongFromTheSameAlbum() {
        val platform = next.copy(title = "Current lyric line")
        assertNull(TrackInfo.current(previous, platform, true))
        assertFalse(previous.matchesSessionTrack(platform, true))
    }

    @Test fun ordinaryTitleChangesAreNotTreatedAsLyrics() {
        val platform = previous.copy(title = "Corrected Song Title")
        assertEquals(platform, TrackInfo.current(previous, platform, false))
        assertFalse(previous.matchesSessionTrack(platform))
    }

    @Test fun missingNativeConnectionStillExposesCurrentSong() {
        assertEquals(next, TrackInfo.current(null, next, false))
        assertNull(TrackInfo.current(null, next.copy(title = "Lyric line"), true))
        assertNull(TrackInfo.current(previous, null, false))
    }
}
