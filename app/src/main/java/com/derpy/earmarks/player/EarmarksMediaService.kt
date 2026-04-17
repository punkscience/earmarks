package com.derpy.earmarks.player

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val NOTIFICATION_CHANNEL_ID = "earmarks_playback"
private const val NOTIFICATION_ID = 1

/**
 * Foreground service hosting the ExoPlayer instance.
 *
 * Extending MediaLibraryService provides:
 *  1. Background playback — foreground service keeps audio alive when switching apps.
 *  2. Android Auto — Auto connects via the MediaBrowserService protocol.
 *
 * DefaultMediaNotificationProvider posts a persistent media notification, which is
 * required for the foreground service to survive on Android 12+ and for Android Auto
 * to recognise this app as a media source.
 */
class EarmarksMediaService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        player = ExoPlayer.Builder(this).build()
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, SessionCallback()).build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setNotificationId(NOTIFICATION_ID)
                .build()
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW  // silent but visible
        ).apply { description = "earwax playback controls" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(ROOT_ITEM, params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val timeline = session.player.currentTimeline
            val window = Timeline.Window()
            val items = ImmutableList.builder<MediaItem>()
            for (i in 0 until timeline.windowCount) {
                items.add(timeline.getWindow(i, window).mediaItem)
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(items.build(), params))
        }
    }

    companion object {
        private val ROOT_ITEM = MediaItem.Builder().setMediaId("earmarks_root").build()
    }
}
