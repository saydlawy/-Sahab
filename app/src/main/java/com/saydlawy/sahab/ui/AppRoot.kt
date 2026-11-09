package com.saydlawy.sahab.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.ui.browse.BrowseScreen
import com.saydlawy.sahab.ui.components.DreamyBackground
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.detail.MovieDetailScreen
import com.saydlawy.sahab.ui.detail.SeriesDetailScreen
import com.saydlawy.sahab.ui.home.FavoritesScreen
import com.saydlawy.sahab.ui.home.HomeScreen
import com.saydlawy.sahab.ui.live.LiveScreen
import com.saydlawy.sahab.ui.login.LoginScreen
import com.saydlawy.sahab.ui.player.PlayerScreen
import com.saydlawy.sahab.ui.settings.SettingsScreen
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AppRoot(container: AppContainer) {
    val nav = rememberNavController()
    var start by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val account = container.prefs.account.first()
        if (account != null) {
            container.repo.setAccount(account)
            container.appScope.launch { container.repo.prewarmCatalog() }
        }
        start = if (account != null) Routes.HOME else Routes.LOGIN
    }

    val light by container.prefs.lightEffects.collectAsStateWithLifecycle(initialValue = false)
    val entry by nav.currentBackStackEntryAsState()
    val onPlayer = entry?.destination?.route == Routes.PLAYER

    val onTab: (String) -> Unit = { route ->
        if (entry?.destination?.route != route) {
            nav.navigate(route) {
                popUpTo(Routes.HOME) { saveState = false }
                launchSingleTop = true
            }
        }
    }

    CompositionLocalProvider(
        LocalLightEffects provides light,
        LocalTabNavigator provides onTab,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isTv = LocalIsTv.current
            val scale = when {
                isTv -> 1f
                maxWidth > maxHeight -> (maxHeight.value / 720f).coerceIn(0.85f, 1.15f)
                else -> (minOf(maxWidth.value, maxHeight.value) / 400f).coerceIn(0.75f, 1.0f)
            }
            CompositionLocalProvider(LocalAppScale provides scale) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Palette.Bg),
                ) {
                    if (!onPlayer) DreamyBackground()
                    val contentModifier =
                        if (onPlayer) Modifier.fillMaxSize()
                        else Modifier.fillMaxSize().safeDrawingPadding()
                    Box(contentModifier) {
                        val startRoute = start
                        if (startRoute != null) AppNavHost(nav, container, startRoute)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, container: AppContainer, start: String) {
    val play: (PlayRequest) -> Unit = { request ->
        container.playback = request
        nav.navigate(Routes.PLAYER)
    }
    val open: (String) -> Unit = { route -> nav.navigate(route) }
    val back: () -> Unit = { nav.popBackStack() }
    val idArgument = listOf(navArgument("id") { type = NavType.StringType })

    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { fadeIn(tween(240)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                container.appScope.launch { container.repo.prewarmCatalog() }
                nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
            })
        }
        composable(Routes.HOME) { HomeScreen(onOpen = open, onPlay = play) }
        composable(Routes.LIVE) { LiveScreen(onBack = back, onPlay = play) }
        composable(Routes.MOVIES) {
            BrowseScreen(series = false, onBack = back, onOpenItem = { open(Routes.movie(it)) }, onPlay = play)
        }
        composable(Routes.SERIES) {
            BrowseScreen(series = true, onBack = back, onOpenItem = { open(Routes.series(it)) }, onPlay = play)
        }
        composable(Routes.FAVORITES) { FavoritesScreen(onBack = back, onOpen = open, onPlay = play) }
        composable(Routes.SPORTS) {
            Column(Modifier.fillMaxSize()) {
                SahabTopBar(current = Routes.SPORTS, onBack = back)
                MessageBox(
                    message = Ar.sportsSoon,
                    actionLabel = Ar.live,
                    onAction = { nav.navigate(Routes.LIVE) { launchSingleTop = true } },
                )
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = back,
                onLoggedOut = {
                    nav.navigate(Routes.LOGIN) { popUpTo(nav.graph.id) { inclusive = true } }
                },
            )
        }
        composable(Routes.MOVIE_PATTERN, arguments = idArgument) { entry ->
            MovieDetailScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = back,
                onPlay = play,
            )
        }
        composable(Routes.SERIES_PATTERN, arguments = idArgument) { entry ->
            SeriesDetailScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = back,
                onPlay = play,
            )
        }
        composable(Routes.PLAYER) { PlayerScreen(container = container, onBack = back) }
    }
}
