package com.ryan1202.lyricsbridge

data class LyricPublication(val raw: String, val status: Long) {
    val timeline = LrcTimeline(raw)

    companion object {
        val empty = LyricPublication("", 1)
        fun matching(text: String) = LyricPublication("[00:00.00]$text", 0)
        const val GUESS_NOTICE = "【模糊匹配，歌词可能不准确】"

        fun from(result: LyricResult, guessNotice: String = GUESS_NOTICE, instrumental: String = "纯音乐"): LyricPublication {
            if (!result.guessed) return LyricPublication(result.raw, if (result.instrumental) 1 else 0)
            val lines = LrcTimeline(result.raw).lines.toMutableList()
            val first = lines.indexOfFirst { it.text.isNotBlank() }
            if (result.instrumental && first < 0) {
                lines.add(LyricLine(0, "$guessNotice $instrumental"))
            } else if (first >= 0 && lines[first].timeMs <= 0) {
                lines[first] = lines[first].copy(text = "$guessNotice ${lines[first].text}")
            } else lines.add(0, LyricLine(0, guessNotice))
            val raw = lines.joinToString("\n") { line ->
                val time = line.timeMs.coerceAtLeast(0)
                "[%02d:%02d.%03d]%s".format(java.util.Locale.ROOT, time / 60_000, time / 1000 % 60, time % 1000, line.text)
            }
            return LyricPublication(raw, if (result.instrumental) 1 else 0)
        }
    }
}
