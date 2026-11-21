package com.ryan1202.lyricsbridge

import android.content.Context

object SyncPreference {
    fun enabled(context: Context) = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
        .getBoolean("enabled", true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("sync", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
    }

    fun islandLyricsEnabled(context: Context) = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
        .getBoolean("island_lyrics", false)

    fun setIslandLyricsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("sync", Context.MODE_PRIVATE).edit().putBoolean("island_lyrics", enabled).apply()
    }
}
