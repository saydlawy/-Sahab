package com.saydlawy.sahab.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saydlawy.sahab.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer is not provided") }

val LocalIsTv = staticCompositionLocalOf { false }

/** True when the person asked for a lighter look (no moving background, no scaling animations). */
val LocalLightEffects = staticCompositionLocalOf { false }

class SimpleFactory(private val build: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(noinline build: (AppContainer) -> VM): VM {
    val container = LocalContainer.current
    return viewModel<VM>(factory = SimpleFactory { build(container) })
}

object Routes {
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
