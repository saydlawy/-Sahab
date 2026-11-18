package com.saydlawy.sahab.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saydlawy.sahab.AppContainer

enum class WidthSizeClass { COMPACT, MEDIUM, EXPANDED }

enum class HeightSizeClass { COMPACT, MEDIUM, EXPANDED }

/**
 * Three independent adaptive display modes:
 * - [PHONE_PORTRAIT]: Vertical single-column mobile layout (video on top, lists below).
 * - [PHONE_LANDSCAPE]: Horizontal mobile layout (video-first or 2-pane compact, no TV clutter).
 * - [TABLET_OR_TV]: Large screen multi-column / full TV OSD layout.
 */
enum class DisplayMode {
    PHONE_PORTRAIT,
    PHONE_LANDSCAPE,
    TABLET_OR_TV,
}

data class AdaptiveWindowInfo(
    val widthClass: WidthSizeClass = WidthSizeClass.COMPACT,
    val heightClass: HeightSizeClass = HeightSizeClass.MEDIUM,
    val mode: DisplayMode = DisplayMode.PHONE_PORTRAIT,
    val widthDp: Dp = 390.dp,
    val heightDp: Dp = 844.dp,
) {
    val isPhone: Boolean get() = mode != DisplayMode.TABLET_OR_TV
    val isPhonePortrait: Boolean get() = mode == DisplayMode.PHONE_PORTRAIT
    val isPhoneLandscape: Boolean get() = mode == DisplayMode.PHONE_LANDSCAPE
    val isLargeScreen: Boolean get() = mode == DisplayMode.TABLET_OR_TV
}

fun computeAdaptiveWindowInfo(
    maxWidth: Dp,
    maxHeight: Dp,
    isTv: Boolean,
): AdaptiveWindowInfo {
    val widthClass = when {
        maxWidth < 600.dp -> WidthSizeClass.COMPACT
        maxWidth < 840.dp -> WidthSizeClass.MEDIUM
        else -> WidthSizeClass.EXPANDED
    }
    val heightClass = when {
        maxHeight < 480.dp -> HeightSizeClass.COMPACT
        maxHeight < 900.dp -> HeightSizeClass.MEDIUM
        else -> HeightSizeClass.EXPANDED
    }
    val smallestDimension = minOf(maxWidth, maxHeight)
    val isLandscape = maxWidth > maxHeight
    val mode = when {
        isTv -> DisplayMode.TABLET_OR_TV
        smallestDimension >= 600.dp -> DisplayMode.TABLET_OR_TV
        isLandscape -> DisplayMode.PHONE_LANDSCAPE
        else -> DisplayMode.PHONE_PORTRAIT
    }
    return AdaptiveWindowInfo(
        widthClass = widthClass,
        heightClass = heightClass,
        mode = mode,
        widthDp = maxWidth,
        heightDp = maxHeight,
    )
}

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer is not provided") }

val LocalIsTv = staticCompositionLocalOf { false }

/** True when the person asked for a lighter look (no moving background, no scaling animations). */
val LocalLightEffects = staticCompositionLocalOf { false }

/** Global scale multiplier (kept at 1f on mobile so Arabic text is never shrunk). */
val LocalAppScale = compositionLocalOf { 1f }

/** Current adaptive window size class and display mode. */
val LocalWindowInfo = compositionLocalOf { AdaptiveWindowInfo() }

val LocalDisplayMode = compositionLocalOf { DisplayMode.PHONE_PORTRAIT }

/** Jumps to a top-level section (the tabs of the top bar) without piling up the back stack. */
val LocalTabNavigator = staticCompositionLocalOf<(String) -> Unit> { {} }

class SimpleFactory(private val build: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    noinline build: (AppContainer) -> VM,
): VM {
    val container = LocalContainer.current
    return viewModel<VM>(key = key, factory = SimpleFactory { build(container) })
}

object Routes {
    const val CLOUD_LOGIN = "cloud_login"
    const val LOGIN = "login"
    const val HOME = "home"
    const val LIVE = "live"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val MOVIE_PATTERN = "movie/{id}"
    const val SERIES_PATTERN = "series/{id}"

    fun movie(id: String) = "movie/$id"
    fun series(id: String) = "series/$id"
}
