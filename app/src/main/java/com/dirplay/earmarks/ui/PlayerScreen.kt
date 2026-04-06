package com.dirplay.earmarks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dirplay.earmarks.player.PlayerState

@Composable
fun PlayerScreen(
    appState: AppState,
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onClearKey: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = { Text("Remove your stored Nostr key. You'll need to enter it again next time.") },
            confirmButton = {
                TextButton(onClick = { onClearKey(); showSettings = false }) { Text("Remove key") }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("earmarks", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.weight(1f))

        // Status / now-playing area
        when (appState) {
            is AppState.Loading -> {
                CircularProgressIndicator(Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(appState.message, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is AppState.Downloading -> {
                CircularProgressIndicator(
                    progress = { appState.done.toFloat() / appState.total },
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("Downloading ${appState.done} / ${appState.total}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is AppState.Playing -> {
                if (playerState.title.isNotBlank()) {
                    Text(playerState.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center)
                }
                if (playerState.artist.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(playerState.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
                if (playerState.album.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(playerState.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
                if (playerState.totalTracks > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("${playerState.currentIndex + 1} / ${playerState.totalTracks}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            is AppState.Error -> {
                Text(appState.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center)
            }
            else -> {}
        }

        Spacer(Modifier.weight(1f))

        // Playback controls — only active when playing
        val controlsEnabled = appState is AppState.Playing
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSkipPrevious, enabled = controlsEnabled) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous",
                    modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onPlayPause, enabled = controlsEnabled,
                modifier = Modifier.size(64.dp)) {
                Icon(
                    if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(48.dp)
                )
            }
            IconButton(onClick = onSkipNext, enabled = controlsEnabled) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next",
                    modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
