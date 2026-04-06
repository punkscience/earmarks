package com.dirplay.earmarks.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dirplay.earmarks.data.Earmark
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PlayerState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val currentIndex: Int = 0,
    val totalTracks: Int = 0
)

class PlayerController(private val context: Context) {

    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    fun connect(onConnected: () -> Unit) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, EarmarksMediaService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get().also { mc ->
                mc.repeatMode = Player.REPEAT_MODE_ALL
                mc.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updateState()
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) = updateState()
                    override fun onPlaybackStateChanged(state: Int) = updateState()
                })
                onConnected()
            }
        }, MoreExecutors.directExecutor())
    }

    fun setPlaylist(files: List<Pair<File, Earmark>>) {
        val shuffled = files.shuffled()
        val items = shuffled.map { (file, earmark) ->
            MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(earmark.title.ifBlank { file.name })
                        .setArtist(earmark.artist.ifBlank { null })
                        .setAlbumTitle(earmark.album.ifBlank { null })
                        .build()
                )
                .build()
        }
        controller?.run {
            setMediaItems(items)
            prepare()
            play()
        }
        _state.value = _state.value.copy(totalTracks = shuffled.size)
    }

    fun playPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipNext() { controller?.seekToNextMediaItem() }
    fun skipPrevious() { controller?.seekToPreviousMediaItem() }

    fun release() { controller?.release(); controller = null }

    private fun updateState() {
        val mc = controller ?: return
        val meta = mc.mediaMetadata
        _state.value = PlayerState(
            isPlaying = mc.isPlaying,
            title = meta.title?.toString() ?: "",
            artist = meta.artist?.toString() ?: "",
            album = meta.albumTitle?.toString() ?: "",
            currentIndex = mc.currentMediaItemIndex,
            totalTracks = mc.mediaItemCount
        )
    }
}
