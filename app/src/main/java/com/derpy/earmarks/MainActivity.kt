package com.derpy.earmarks

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
import com.derpy.earmarks.ui.AppState
import com.derpy.earmarks.ui.KeyEntryScreen
import com.derpy.earmarks.ui.MainViewModel
import com.derpy.earmarks.ui.PlayerScreen

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
                    val stats by vm.stats.collectAsState()
                    val notice by vm.notice.collectAsState()

                    if (appState is AppState.KeyMissing) {
                        KeyEntryScreen(onSaveKey = { vm.saveKey(it) })
                    } else {
                        PlayerScreen(
                            appState = appState,
                            playerState = playerState,
                            stats = stats,
                            notice = notice,
                            onDismissNotice = { vm.dismissNotice() },
                            onPlayPause = { vm.player.playPause() },
                            onSkipNext = { vm.player.skipNext() },
                            onSkipPrevious = { vm.player.skipPrevious() },
                            onDeleteCurrent = { vm.deleteCurrent() },
                            onClearKey = { vm.clearKey() }
                        )
                    }
                }
            }
        }
    }
}
