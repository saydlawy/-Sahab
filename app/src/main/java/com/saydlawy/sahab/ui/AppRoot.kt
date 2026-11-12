package com.saydlawy.sahab.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.saydlawy.sahab.ui.sports.SportsScreen
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
import com.saydlawy.sahab.ui.components.SyncOverlay
import com.saydlawy.sahab.data.Repository
import androidx.compose.ui.platform.LocalContext
import android.util.Log

@Composable
fun AppRoot(container: AppContainer) {
    val context = LocalContext.current
    val nav = rememberNavController()
    var start by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val account = container.prefs.account.first()
        if (account != null) {
            container.repo.setAccount(account)
            container.appScope.launch {
                // 1. Seed population (only if DB empty or no enrichment yet)
                val stats = container.catalogSync.populateFromSeed(context)
                Log.d("SahabSeed", "seed: total=${stats.total} loaded=${stats.loaded} skipped=${stats.wasSkipped} dur=${stats.durationMs}ms")

                // 2. Standard prewarm + delta sync
                container.repo.prewarmCatalog()
                val sync = container.repo.syncCatalog(force = false)
                if (sync is Repository.SyncResult.Success || sync is Repository.SyncResult.Failure) {
                    container.catalogSync.syncMovies(container.repo)
                }
            }
        }
        start = if (account != null) Routes.HOME else Routes.LOGIN
    }

    val light by container.prefs.lightEffects.collectAsStateWithLifecycle(initialValue = false)
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val onPlayer = currentRoute == Routes.PLAYER
    val onSports = currentRoute == Routes.SPORTS

    val onTab: (String) -> Unit = remember(nav) {
        { route ->
            if (nav.currentDestination?.route != route) {
                nav.navigate(route) {
                    popUpTo(Routes.HOME) { saveState = false }
                    launchSingleTop = true
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalLightEffects provides light,
        LocalTabNavigator provides onTab,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isTv = LocalIsTv.current
            val windowInfo = computeAdaptiveWindowInfo(maxWidth, maxHeight, isTv)
            val scale = 1f
            CompositionLocalProvider(
                LocalAppScale provides scale,
                LocalWindowInfo provides windowInfo,
                LocalDisplayMode provides windowInfo.mode,
            ) {
                val layoutDir = LocalLayoutDirection.current
                val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (onSports) Color(0xFF0C0919) else Palette.Bg),
                ) {
                    if (!onPlayer && !onSports) DreamyBackground()
                    val contentModifier = when {
                        onPlayer && windowInfo.mode != DisplayMode.PHONE_PORTRAIT -> Modifier.fillMaxSize()
                        windowInfo.isPhoneLandscape -> {
                            // Keep symmetric horizontal margins across notch/cutout on landscape phones
                            val symH = maxOf(
                                safeInsets.calculateStartPadding(layoutDir),
                                safeInsets.calculateEndPadding(layoutDir),
                            )
                            Modifier.fillMaxSize().padding(
                                start = symH,
                                end = symH,
                                top = safeInsets.calculateTopPadding(),
                                bottom = safeInsets.calculateBottomPadding(),
                            )
                        }
                        else -> Modifier.fillMaxSize().safeDrawingPadding()
                    }
                    Box(contentModifier) {
                        val startRoute = start
                        if (startRoute != null) AppNavHost(nav, container, startRoute)
                    }

                    val repoSyncing by container.repo.syncing.collectAsStateWithLifecycle()
                    val catalogSyncing by container.catalogSync.syncing.collectAsStateWithLifecycle()
                    val syncing = repoSyncing || catalogSyncing
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        SyncOverlay(
                            visible = syncing && !onPlayer,
                            modifier = Modifier.padding(bottom = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, container: AppContainer, start: String) {
    val context = LocalContext.current
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
        enterTransition = { fadeIn(tween(140)) },
        exitTransition = { fadeOut(tween(100)) },
        popEnterTransition = { fadeIn(tween(140)) },
        popExitTransition = { fadeOut(tween(100)) },
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                container.appScope.launch {
                    val stats = container.catalogSync.populateFromSeed(context)
                    Log.d("SahabSeed", "seed (login): total=${stats.total} loaded=${stats.loaded} skipped=${stats.wasSkipped}")
                    container.repo.prewarmCatalog()
                    val sync = container.repo.syncCatalog(force = false)
                    if (sync is Repository.SyncResult.Success || sync is Repository.SyncResult.Failure) {
                        container.catalogSync.syncMovies(container.repo)
                    }
                }
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
            SportsScreen(
                onClose = back,
                onOpenLive = {
                    nav.navigate(Routes.LIVE) {
                        launchSingleTop = true
                    }
                },
            )
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
