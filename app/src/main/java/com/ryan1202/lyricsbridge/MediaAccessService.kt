package com.ryan1202.lyricsbridge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaSessionManager

class MediaAccessService : NotificationListenerService() {
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { startAutomatically() }
    override fun onListenerConnected() {
        super.onListenerConnected()
        BridgeStatus.accessConnected = true
        getSystemService(MediaSessionManager::class.java).addOnActiveSessionsChangedListener(
            sessionsListener, ComponentName(this, MediaAccessService::class.java))
        startAutomatically()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.apple.android.music") startAutomatically()
    }
    private fun startAutomatically() {
        if (!BridgeStatus.accessConnected || !SyncPreference.enabled(this) || BridgeStatus.ui.value.running) return
        try {
            val hasMusic = getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(this, MediaAccessService::class.java))
                .any { it.packageName == "com.apple.android.music" }
            if (hasMusic) startForegroundService(Intent(this, BridgeService::class.java).setAction("auto"))
        } catch (_: SecurityException) {
            BridgeStatus.summary = localizedString(R.string.reenable_access)
        } catch (_: IllegalStateException) {
            BridgeStatus.summary = localizedString(R.string.background_start_blocked)
        }
    }
    override fun onListenerDisconnected() {
        getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsListener)
        BridgeStatus.accessConnected = false
        super.onListenerDisconnected()
    }
    override fun onDestroy() {
        getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsListener)
        super.onDestroy()
    }
}

object BridgeStatus {
    val ui = kotlinx.coroutines.flow.MutableStateFlow(BridgeUiState())
    @Volatile var accessConnected = false
    @Volatile var summary = ""
    @Volatile var lyric = ""
    @Volatile var mode = ""
}
