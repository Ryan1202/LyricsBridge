package com.ryan1202.lyricsbridge

import android.app.Application
import android.os.Build
import androidx.core.os.LocaleListCompat
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

class LyricsBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT < 33) {
            val tag = getSharedPreferences("language", MODE_PRIVATE).getString("tag", "").orEmpty()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }
}

fun Context.localizedString(@StringRes id: Int, vararg args: Any): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    val localized = if (locales.isEmpty) this else {
        val configuration = Configuration(resources.configuration)
        configuration.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
        createConfigurationContext(configuration)
    }
    return localized.getString(id, *args)
}
