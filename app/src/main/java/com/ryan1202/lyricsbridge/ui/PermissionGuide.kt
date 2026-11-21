package com.ryan1202.lyricsbridge.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryan1202.lyricsbridge.R

@Composable
internal fun PermissionGuide(
    accessGranted: Boolean,
    notificationsGranted: Boolean,
    onOpenAccessSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val isXiaomi = manufacturer in setOf("xiaomi", "redmi", "poco")
    val accessInstructions = when {
        isXiaomi -> R.string.notification_access_hyperos
        manufacturer in setOf("vivo", "iqoo") -> R.string.notification_access_originos
        manufacturer in setOf("oppo", "oneplus", "realme") -> R.string.notification_access_coloros
        else -> R.string.notification_access_manual
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_guide)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PermissionSection(
                    title = stringResource(R.string.notification_access),
                    description = stringResource(R.string.notification_access_detail),
                    granted = accessGranted,
                    onOpenSettings = onOpenAccessSettings
                )
                if (!accessGranted) {
                    Text(stringResource(R.string.notification_access_steps))
                    NotificationAccessHelp(accessInstructions, onOpenSystemSettings)
                }
                HorizontalDivider()
                PermissionSection(
                    title = stringResource(R.string.status_notifications),
                    description = stringResource(R.string.status_notifications_detail),
                    granted = notificationsGranted,
                    onOpenSettings = onOpenNotificationSettings
                )
                if (isXiaomi) {
                    HorizontalDivider()
                    XiaomiBatterySection(onOpenBatterySettings)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )
}

@Composable
private fun NotificationAccessHelp(
    @StringRes instructions: Int,
    onOpenSystemSettings: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(if (expanded) R.string.hide_permission_help else R.string.find_permission))
    }
    if (expanded) {
        Text(stringResource(instructions))
        if (Build.VERSION.SDK_INT >= 33) {
            Text(stringResource(R.string.notification_access_restricted))
        }
        OutlinedButton(onClick = onOpenSystemSettings) {
            Text(stringResource(R.string.open_system_settings_home))
        }
    }
}

@Composable
private fun XiaomiBatterySection(onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.xiaomi_battery_policy),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.xiaomi_battery_policy_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.open_system_settings))
        }
    }
}

@Composable
private fun PermissionSection(
    title: String,
    description: String,
    granted: Boolean,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (granted) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.enabled),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!granted) {
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.open_system_settings))
            }
        }
    }
}
