package com.saydlawy.sahab.ui.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization
import com.saydlawy.sahab.ui.theme.Palette

// Design tokens from ExoPlayer/DESIGN.md & media-player/DESIGN.md
internal val OsdSurfaceLowest get() = Palette.color("OsdSurfaceLowest")
internal val OsdSurfaceLow get() = Palette.color("OsdSurfaceLow")
internal val OsdSurfaceContainer get() = Palette.color("OsdSurfaceContainer")
internal val OsdSurfaceHigh get() = Palette.color("OsdSurfaceHigh")
internal val OsdSurfaceHighest get() = Palette.color("OsdSurfaceHighest")
internal val OsdPrimary get() = Palette.color("OsdPrimary")
internal val OsdPrimaryContainer get() = Palette.color("OsdPrimaryContainer")
internal val OsdOnPrimary get() = Palette.color("OsdOnPrimary")
internal val OsdCyan get() = Palette.color("OsdCyan")
internal val OsdTertiary get() = Palette.color("OsdTertiary")
internal val OsdOnSurface get() = Palette.color("OsdOnSurface")
internal val OsdOnSurfaceVariant get() = Palette.color("OsdOnSurfaceVariant")
internal val OsdOutline get() = Palette.color("OsdOutline")
internal val OsdOutlineVariant get() = Palette.color("OsdOutlineVariant")

@OptIn(UnstableApi::class)
private fun defaultResizeModes(): IntArray = intArrayOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
)

internal val ResizeModes = defaultResizeModes()

internal val PlaybackSpeeds = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)

internal const val SEEK_STEP_MS = 10_000L
internal const val CONTROLS_HIDE_MS = 2_000L
internal val TimelineProgressBrush get() = Brush.horizontalGradient(listOf(OsdPrimaryContainer, OsdPrimary, OsdCyan))

@Composable
fun PlayerScreen(container: AppContainer, onBack: () -> Unit) {
    val themeCustomization = LocalThemeCustomization.current
    val request = remember { container.playback ?: container.restorePendingPlayback() }
    if (request == null || request.items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val exitPlayer: () -> Unit = {
        container.clearPendingPlayback()
        onBack()
    }
    PlayerContent(container, request, exitPlayer)
}
