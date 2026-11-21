package com.ryan1202.lyricsbridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ryan1202.lyricsbridge.BridgeUiState
import com.ryan1202.lyricsbridge.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    languageTag: String,
    onOpenPermissionGuide: () -> Unit,
    onLanguageChange: (String) -> Unit,
    islandLyricsEnabled: Boolean,
    onIslandLyricsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.sync_settings),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(R.string.settings_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .toggleable(value = islandLyricsEnabled, role = Role.Switch, onValueChange = onIslandLyricsChange)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.island_lyrics), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.island_lyrics_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = islandLyricsEnabled, onCheckedChange = null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = onOpenPermissionGuide) {
                Text(stringResource(R.string.permission_guide))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LanguageOptions(languageTag, onLanguageChange)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsInformation()
        }
    }
}

@Composable
private fun LanguageOptions(
    languageTag: String,
    onLanguageChange: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.language),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp)
    )
    val options = listOf(
        "" to R.string.language_system,
        "zh" to R.string.language_chinese,
        "en" to R.string.language_english
    )
    options.forEach { (tag, label) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = languageTag == tag,
                    role = Role.RadioButton,
                    onClick = { onLanguageChange(tag) }
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = languageTag == tag,
                onClick = null
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(label),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun SettingsInformation() {
    Text(
        text = stringResource(R.string.system_appearance),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp)
    )
    Text(
        text = stringResource(R.string.system_appearance_detail),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = stringResource(R.string.lyric_sources),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp)
    )
    Text(
        text = stringResource(R.string.lyric_sources_detail),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun MatchDetailsDialog(
    state: BridgeUiState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (state.guessed) {
                    stringResource(R.string.guessed_match)
                } else {
                    stringResource(R.string.lyric_match)
                }
            )
        },
        text = {
            Text("${providerName(state.provider)}\n\n${state.match}")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun SettingsPreview() {
    SettingsSheet(
        languageTag = "en",
        onOpenPermissionGuide = {},
        onLanguageChange = {},
        islandLyricsEnabled = true,
        onIslandLyricsChange = {},
        onDismiss = {}
    )
}
