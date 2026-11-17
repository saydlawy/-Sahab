package com.saydlawy.sahab.ui.player

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * O-03: Coordinates the active [Player] with [SahabPlaybackService] (background live audio)
 * and phone Picture-in-Picture state without affecting Android TV behavior.
 */
object SahabMediaSessionBridge {
    @Volatile
    var activePlayer: Player? = null
        private set

    @Volatile
    var pipAllowed: Boolean = false

    val inPipMode = MutableStateFlow(false)

    fun attach(context: Context, player: Player, allowBackgroundService: Boolean) {
        activePlayer = player
        if (allowBackgroundService) {
            runCatching {
                context.applicationContext.startService(
                    Intent(context.applicationContext, SahabPlaybackService::class.java),
                )
            }
        }
    }

    fun detach(context: Context, player: Player) {
        if (activePlayer === player) {
            activePlayer = null
            pipAllowed = false
            inPipMode.value = false
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, SahabPlaybackService::class.java),
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
class SahabPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = SahabMediaSessionBridge.activePlayer ?: return
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (mediaSession == null) {
            val player = SahabMediaSessionBridge.activePlayer ?: return null
            mediaSession = MediaSession.Builder(this, player).build()
        }
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
