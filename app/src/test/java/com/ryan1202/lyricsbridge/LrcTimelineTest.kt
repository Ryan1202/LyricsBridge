package com.ryan1202.lyricsbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcTimelineTest {
    @Test fun schedulesOnlyTheNextLineAtPlaybackSpeed() {
        val lrc = LrcTimeline("[00:01.00]First\n[00:05.00]Next")
        assertEquals(4000L, lrc.delayUntilNextLine(1000, 1f))
        assertEquals(2000L, lrc.delayUntilNextLine(1000, 2f))
        assertEquals(null, lrc.delayUntilNextLine(1000, 0f))
        assertEquals(null, lrc.delayUntilNextLine(5000, 1f))
    }
    @Test fun repeatedTimestampsAndFractions() {
        val lrc = LrcTimeline("[00:01.5][00:03.050]一行\n[00:02]另一行")
        assertEquals(listOf(1500L, 2000L, 3050L), lrc.lines.map { it.timeMs })
        assertEquals("", lrc.at(1499))
        assertEquals("一行", lrc.at(1500))
        assertEquals("另一行", lrc.at(2500))
        assertEquals("一行", lrc.at(3050))
    }
    @Test fun positiveOffsetAdvancesLyrics() {
        val lrc = LrcTimeline("[offset:500]\n[00:01.00]提前")
        assertEquals("提前", lrc.at(500))
    }
}
