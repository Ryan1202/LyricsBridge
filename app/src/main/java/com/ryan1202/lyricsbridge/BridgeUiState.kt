package com.ryan1202.lyricsbridge

import android.graphics.Bitmap

data class BridgeUiState(
    val running: Boolean = false,
    val song: TrackInfo? = null,
    val artwork: Bitmap? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val fetching: Boolean = false,
    val lyric: String = "",
    val lyricTimeMs: Long? = null,
    val previousLyric: String = "",
    val nextLyric: String = "",
    val provider: String = "",
    val match: String = "",
    val guessed: Boolean = false,
    val error: String? = null,
    val status: String = ""
)
