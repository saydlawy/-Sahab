package com.saydlawy.sahab.ui.player

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * LIVE-01: Detects the "frozen picture while audio keeps playing" deadlock.
 *
 * Some hardware video decoders (observed on Amlogic `OMX.amlogic.*.decoder.awesome2` on the TCL TV)
 * silently stop returning output buffers. No [androidx.media3.common.PlaybackException] is thrown,
 * so `onPlayerError` never fires, while the audio renderer (the master clock) keeps advancing.
 *
 * The watchdog samples the video decoder's rendered-frame counter once per second. When the player
 * is READY + playing, a video track is active, no frame has been rendered for [STALL_MS] and the
 * playback position (audio clock) advanced at least [MIN_AUDIO_ADVANCE_MS], it recovers:
 *  1. `stop()` (releases the stuck codec) + `seekToDefaultPosition()` + `prepare()` at the live edge.
 *  2. From the second stall onwards, [onEscalate] may switch the stream container (m3u8 <-> ts).
 * At most [MAX_RECOVERIES] recoveries per [RECOVERY_WINDOW_MS] per item, so still-image / very
 * low-fps channels can never cause a reset loop.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveVideoStallWatchdog(
    player: ExoPlayer?,
    enabled: Boolean,
    itemKey: Any?,
    onEscalate: () -> Boolean = { false },
) {
    val escalate by rememberUpdatedState(onEscalate)
    LaunchedEffect(player, enabled, itemKey) {
        if (player == null || !enabled) return@LaunchedEffect
        var lastRendered = -1
        var frozenSinceMs = 0L
        var positionAtFreezeMs = 0L
        var recoveries = 0
        var windowStartMs = SystemClock.elapsedRealtime()

        while (isActive) {
            delay(SAMPLE_MS)
            val counters = player.videoDecoderCounters
            if (counters == null) {
                lastRendered = -1
                continue
            }

            val armed = player.isPlaying &&
                player.playbackState == Player.STATE_READY &&
                player.videoFormat != null
            if (!armed) {
                lastRendered = -1
                continue
            }

            counters.ensureUpdated()
            val rendered = counters.renderedOutputBufferCount
            val now = SystemClock.elapsedRealtime()
            val position = player.currentPosition
            if (rendered != lastRendered) {
                lastRendered = rendered
                frozenSinceMs = now
                positionAtFreezeMs = position
                continue
            }

            val frozenFor = now - frozenSinceMs
            val audioAdvanced = position - positionAtFreezeMs
            if (frozenFor < STALL_MS || audioAdvanced < MIN_AUDIO_ADVANCE_MS) continue

            if (now - windowStartMs > RECOVERY_WINDOW_MS) {
                windowStartMs = now
                recoveries = 0
            }
            if (recoveries >= MAX_RECOVERIES) {
                // Give up for this item: probably a still-image / ultra low-fps channel.
                frozenSinceMs = now
                positionAtFreezeMs = position
                continue
            }
            recoveries++
            Log.w(
                TAG,
                "Live video stall: no frame for ${frozenFor}ms while audio advanced " +
                    "${audioAdvanced}ms (rendered=$rendered, dropped=${counters.droppedBufferCount}) " +
                    "-> recovery #$recoveries",
            )
            // stop() resets the renderers, which releases the deadlocked MediaCodec instance.
            player.stop()
            val escalated = recoveries >= 2 && escalate()
            if (!escalated) {
                player.seekToDefaultPosition()
                player.prepare()
                player.play()
            }
            lastRendered = -1
        }
    }
}

private const val TAG = "SahabLiveWatchdog"
private const val SAMPLE_MS = 1_000L
private const val STALL_MS = 4_500L
private const val MIN_AUDIO_ADVANCE_MS = 3_000L
private const val MAX_RECOVERIES = 3
private const val RECOVERY_WINDOW_MS = 120_000L
