package com.ryan1202.lyricsbridge

data class LyricLine(val timeMs: Long, val text: String)

class LrcTimeline(val raw: String) {
    val lines: List<LyricLine>
    init {
        val stamp = Regex("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?]")
        val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        lines = raw.lineSequence().flatMap { row ->
            val matches = stamp.findAll(row).toList()
            val text = row.substring(matches.lastOrNull()?.range?.last?.plus(1) ?: row.length).trim()
            matches.map { match ->
                val (minutes, seconds, fraction) = match.destructured
                LyricLine(minutes.toLong() * 60_000 + seconds.toLong() * 1000 +
                    fraction.padEnd(3, '0').toLong() - offset, text)
            }
        }.sortedBy { it.timeMs }.toList()
    }
    fun at(positionMs: Long): String = lines.lastOrNull { it.timeMs <= positionMs }?.text.orEmpty()
    fun delayUntilNextLine(positionMs: Long, speed: Float): Long? {
        if (speed <= 0f) return null
        val next = lines.firstOrNull { it.timeMs > positionMs } ?: return null
        return kotlin.math.ceil((next.timeMs - positionMs) / speed.toDouble()).toLong().coerceAtLeast(1)
    }
    companion object {
        val demo = """[00:00.00]第一句：歌词桥接测试
[00:05.00]第二句：检查 CarWith 的歌词显示
[00:10.00]第三句：检查暂停与继续
[00:15.00]第四句：检查进度同步
[00:20.00]测试结束，可重新开始"""
    }
}
