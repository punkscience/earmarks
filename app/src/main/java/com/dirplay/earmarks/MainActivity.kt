package com.dirplay.earmarks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dirplay.earmarks.ui.AppState
import com.dirplay.earmarks.ui.KeyEntryScreen
import com.dirplay.earmarks.ui.MainViewModel
import com.dirplay.earmarks.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val vm: MainViewModel = viewModel()
                    val appState by vm.state.collectAsState()
                    val playerState by vm.playerState.collectAsState()

                    if (appState is AppState.KeyMissing) {
                        KeyEntryScreen(onSaveKey = { vm.saveKey(it) })
                    } else {
                        PlayerScreen(
                            appState = appState,
                            playerState = playerState,
                            onPlayPause = { vm.player.playPause() },
                            onSkipNext = { vm.player.skipNext() },
                            onSkipPrevious = { vm.player.skipPrevious() },
                            onClearKey = { vm.clearKey() }
                        )
                    }
                }
            }
        }
    }
}
