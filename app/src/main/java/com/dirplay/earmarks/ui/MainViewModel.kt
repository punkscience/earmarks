package com.dirplay.earmarks.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dirplay.earmarks.blossom.BlossomService
import com.dirplay.earmarks.data.Earmark
import com.dirplay.earmarks.data.EarmarkCache
import com.dirplay.earmarks.data.KeyStore
import com.dirplay.earmarks.nostr.Bech32
import com.dirplay.earmarks.nostr.NostrService
import com.dirplay.earmarks.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface AppState {
    object KeyMissing : AppState
    data class Loading(val message: String) : AppState
    data class Downloading(val done: Int, val total: Int) : AppState
    data class Playing(val earmarks: List<Earmark>) : AppState
    data class Error(val message: String) : AppState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val keyStore = KeyStore(app)
    private val nostrService = NostrService(httpClient)
    private val blossomService = BlossomService(httpClient)
    val cache = EarmarkCache(app)
    val player = PlayerController(app)

    private val _state = MutableStateFlow<AppState>(AppState.Loading("Starting…"))
    val state: StateFlow<AppState> = _state.asStateFlow()

    val playerState get() = player.state

    init {
        player.connect { /* controller ready */ }
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                _state.value = AppState.Loading("Loading…")

                val privKeyHex = keyStore.getKey()
                if (privKeyHex == null) {
                    _state.value = AppState.KeyMissing
                    return@launch
                }

                _state.value = AppState.Loading("Connecting to Nostr relays…")
                val earmarks = nostrService.fetchEarmarks(privKeyHex)

                if (earmarks.isEmpty()) {
                    _state.value = AppState.Error("No earmarks found")
                    return@launch
                }

                // Prune files for earmarks that are no longer in the list
                val activeTsSet = earmarks.map { it.ts }.toSet()
                cache.pruneExpired(activeTsSet)

                // Download any earmarks not already cached
                val uncached = earmarks.filter { cache.getCachedFile(it) == null }
                val total = uncached.size
                var done = 0

                if (total > 0) {
                    _state.value = AppState.Downloading(0, total)
                    for (earmark in uncached) {
                        val destFile = cache.targetFile(earmark)
                        blossomService.downloadAndDecrypt(earmark, destFile)
                        done++
                        _state.value = AppState.Downloading(done, total)
                    }
                }

                // Build playlist from all cached files (shuffle happens inside PlayerController)
                val playlist = earmarks.mapNotNull { earmark ->
                    cache.getCachedFile(earmark)?.let { it to earmark }
                }

                if (playlist.isEmpty()) {
                    _state.value = AppState.Error("No playable tracks available")
                    return@launch
                }

                player.setPlaylist(playlist)
                _state.value = AppState.Playing(earmarks)

            } catch (e: Exception) {
                _state.value = AppState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /** Validates and stores a new key from the key entry screen. */
    fun saveKey(input: String): Result<Unit> = runCatching {
        val hex = when {
            input.startsWith("nsec1", ignoreCase = true) ->
                Bech32.decodeNsec(input).toHex()
            input.length == 64 && input.all { it.isHexDigit() } ->
                input.lowercase()
            else -> error("Invalid key — paste an nsec1 or 64-char hex private key")
        }
        viewModelScope.launch { keyStore.saveKey(hex); load() }
    }

    fun clearKey() {
        viewModelScope.launch { keyStore.clearKey(); _state.value = AppState.KeyMissing }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
