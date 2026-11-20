package com.saydlawy.sahab.ui.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.saydlawy.sahab.MainActivity
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

    private fun buildSession(player: Player): MediaSession {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        syncActiveSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncActiveSession()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun syncActiveSession() {
        val player = SahabMediaSessionBridge.activePlayer ?: return
        val existing = mediaSession
        if (existing == null) {
            mediaSession = buildSession(player)
        } else if (existing.player !== player) {
            existing.player = player
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (!isAllowedMediaController(controllerInfo.uid, android.os.Process.myUid(), controllerInfo.isTrusted)) {
            return null
        }
        syncActiveSession()
        return mediaSession
    }

    internal companion object {
        fun isAllowedMediaController(uid: Int, myUid: Int, isTrusted: Boolean): Boolean =
            uid == myUid || isTrusted
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) {
            mediaSession?.release()
            mediaSession = null
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
