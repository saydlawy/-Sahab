package com.saydlawy.sahab.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.saydlawy.sahab.data.HomeChannelsLayout
import com.saydlawy.sahab.data.HomeChannelsSource

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.saydlawy.sahab.ui.components.GlassPanel
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.ui.browse.BrowseScreen
import com.saydlawy.sahab.ui.components.DreamyBackground
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.OledNavigationChrome
import com.saydlawy.sahab.ui.detail.MovieDetailScreen
import com.saydlawy.sahab.ui.detail.SeriesDetailScreen
import com.saydlawy.sahab.ui.home.FavoritesScreen
import com.saydlawy.sahab.ui.home.HomeScreen
import com.saydlawy.sahab.ui.live.LiveScreen
import com.saydlawy.sahab.ui.login.LoginScreen
import com.saydlawy.sahab.ui.login.CloudLoginScreen
import com.saydlawy.sahab.ui.player.PlayerScreen
import com.saydlawy.sahab.ui.settings.SettingsScreen
import androidx.activity.compose.BackHandler
import com.saydlawy.sahab.ui.focus.FocusNavigationController
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization
import com.saydlawy.sahab.ui.theme.isVisible
import com.saydlawy.sahab.util.Ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.saydlawy.sahab.ui.components.SyncOverlay
import com.saydlawy.sahab.data.Repository
import androidx.compose.ui.platform.LocalContext
import android.util.Log

@Composable
fun AppRoot(container: AppContainer, onReady: () -> Unit = {}) {
    val context = LocalContext.current
    val nav = rememberNavController()
    var start by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(start) {
        if (start != null) onReady()
    }

    LaunchedEffect(container.userSyncService) {
        container.userSyncService.syncWriteErrors.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val localAccount = container.prefs.account.first()
        if (localAccount != null) {
            container.repo.setAccount(localAccount)
            start = Routes.HOME
            onReady()
            if (container.authManager.currentUser != null) {
                container.ensureAutoSyncStarted()
                container.syncCoordinator.syncFcmToken(context)
                container.appScope.launch {
                    container.syncCoordinator.performCloudRestoreAndMerge(waitForMerge = false)
                }
            }
            container.syncCoordinator.triggerFullSync(context, forceCatalog = false, reason = "startup_local")
        } else if (container.authManager.currentUser != null) {
            container.ensureAutoSyncStarted()
            container.syncCoordinator.syncFcmToken(context)
            val restoredAccount = container.syncCoordinator.performCloudRestoreAndMerge(
                waitForMerge = false,
                accountTimeoutMs = 2_800L,
            )
            if (restoredAccount != null) {
                container.repo.setAccount(restoredAccount)
                start = Routes.HOME
                onReady()
                container.syncCoordinator.triggerFullSync(context, forceCatalog = false, reason = "startup_cloud")
            } else {
                start = Routes.CLOUD_LOGIN
                onReady()
            }
        } else {
            start = Routes.CLOUD_LOGIN
            onReady()
        }
    }

    val light by container.prefs.lightEffects.collectAsStateWithLifecycle(initialValue = false)
    val themePreferences by container.prefs.themePreferences.collectAsStateWithLifecycle(initialValue = com.saydlawy.sahab.data.ThemePreferences())
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val onPlayer = currentRoute == Routes.PLAYER
    val focusController = remember { FocusNavigationController() }
    var previousScreenKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entry?.id, currentRoute) {
        val resolvedKey = when (currentRoute) {
            Routes.MOVIE_PATTERN -> "movie/${entry?.arguments?.getString("id").orEmpty()}"
            Routes.SERIES_PATTERN -> "series/${entry?.arguments?.getString("id").orEmpty()}"
            else -> currentRoute
        }
        if (resolvedKey != null) {
            if (previousScreenKey != null && previousScreenKey != resolvedKey) {
                focusController.onScreenResumed(resolvedKey)
            }
            previousScreenKey = resolvedKey
        }
    }

    val onTab: (String) -> Unit = remember(nav, focusController) {
        { route ->
            if (nav.currentDestination?.route != route && focusController.canNavigateNow()) {
                focusController.onScreenResumed(route)
                nav.navigate(route) {
                    popUpTo(Routes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalLightEffects provides light,
        LocalTabNavigator provides onTab,
        LocalFocusNavigation provides focusController,
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
                        .background(Palette.Bg),
                ) {
                    if (!onPlayer) DreamyBackground()
                    val contentModifier = when {
                        windowInfo.mode != DisplayMode.PHONE_PORTRAIT -> Modifier.fillMaxSize()
                        else -> Modifier.fillMaxSize().safeDrawingPadding()
                    }
                    val oledPhoneNavigation = themePreferences.selectedThemeId == "oled_cinema" && windowInfo.mode == DisplayMode.PHONE_PORTRAIT
                    val oledWideNavigation = themePreferences.selectedThemeId == "oled_cinema" && windowInfo.mode == DisplayMode.TABLET_OR_TV
                    val navigationVisible = LocalThemeCustomization.current.isVisible("global_navigation")
                    val adjustedContentModifier = when {
                        onPlayer -> contentModifier
                        oledPhoneNavigation && navigationVisible ->
                            contentModifier.padding(bottom = 76.dp)
                        oledWideNavigation && navigationVisible ->
                            contentModifier.padding(start = 84.dp)
                        else -> contentModifier
                    }
                    Box(adjustedContentModifier) {
                        val startRoute = start
                        if (startRoute != null) AppNavHost(nav, container, startRoute)
                    }

                    if (!onPlayer && navigationVisible && (oledPhoneNavigation || oledWideNavigation)) {
                        val navigationPlacement = LocalThemeCustomization.current.componentPlacement["global_navigation"] ?: "center"
                        val chromeAlignment = when {
                            oledPhoneNavigation -> Alignment.BottomCenter
                            navigationPlacement == "start" -> Alignment.TopStart
                            navigationPlacement == "end" -> Alignment.BottomStart
                            else -> Alignment.CenterStart
                        }
                        Box(
                            modifier = Modifier.fillMaxSize().padding(safeInsets),
                            contentAlignment = chromeAlignment,
                        ) {
                            OledNavigationChrome(currentRoute, onTab, windowInfo.mode)
                        }
                    }

                    RootSyncStatusOverlay(container = container, onPlayer = onPlayer)

                    NotificationPermissionPrompt(
                        currentRoute = currentRoute,
                        isTv = isTv,
                    )

                    if (!onPlayer) {
                        SyncPinUnlockDialog(
                            container = container,
                            nav = nav,
                        )
                    }

                    GithubUpdatePrompt(
                        container = container,
                        visible = !onPlayer,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, container: AppContainer, start: String) {
    val context = LocalContext.current
    val focusController = LocalFocusNavigation.current
    val play: (PlayRequest) -> Unit = { request ->
        if (focusController.canNavigateNow()) {
            container.playback = request
            container.savePendingPlayback(request)
            nav.navigate(Routes.PLAYER) { launchSingleTop = true }
        }
    }
    val open: (String) -> Unit = { route ->
        if (focusController.canNavigateNow()) {
            nav.navigate(route) { launchSingleTop = true }
        }
    }
    val back: () -> Unit = {
        nav.popBackStack()
    }
    val idArgument = listOf(navArgument("id") { type = NavType.StringType })

    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { fadeIn(tween(140)) },
        exitTransition = { fadeOut(tween(100)) },
        popEnterTransition = { fadeIn(tween(140)) },
        popExitTransition = { fadeOut(tween(100)) },
    ) {
        composable(Routes.CLOUD_LOGIN) {
            val scope = rememberCoroutineScope()
            var restoringCloud by remember { mutableStateOf(false) }
            val fromSettings = remember { nav.previousBackStackEntry?.destination?.route == Routes.SETTINGS }

            BackHandler(enabled = fromSettings && !restoringCloud) {
                nav.popBackStack()
            }

            Box(Modifier.fillMaxSize()) {
                CloudLoginScreen(
                    skipLabel = if (fromSettings) "رجوع" else "تخطي الآن (بدون حفظ سحابي)",
                    onSuccess = {
                        if (!restoringCloud) {
                            restoringCloud = true
                            scope.launch {
                                container.ensureAutoSyncStarted()
                                container.syncCoordinator.syncFcmToken(context)
                                val restoredAccount = runCatching {
                                    container.syncCoordinator.performCloudRestoreAndMerge()
                                }.getOrNull()
                                restoringCloud = false
                                if (restoredAccount != null) {
                                    container.prefs.setActiveAccount(restoredAccount.id)
                                    container.repo.setAccount(restoredAccount)
                                    container.syncCoordinator.triggerFullSync(
                                        context = context,
                                        forceCatalog = false,
                                        reason = "cloud_login_restore",
                                    )
                                    nav.navigate(Routes.HOME) {
                                        popUpTo(Routes.CLOUD_LOGIN) { inclusive = true }
                                    }
                                } else {
                                    nav.navigate(Routes.LOGIN) {
                                        popUpTo(Routes.CLOUD_LOGIN) { inclusive = true }
                                    }
                                }
                            }
                        }
                    },
                    onSkip = {
                        if (fromSettings) {
                            nav.popBackStack()
                        } else {
                            // مسار التخطي بدون حساب سحابي: لا يتم تفعيل أي رفع سحابي
                            container.mergeCoordinator.reset()
                            nav.navigate(Routes.LOGIN) { popUpTo(Routes.CLOUD_LOGIN) { inclusive = true } }
                        }
                    }
                )

                if (restoringCloud) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.68f))
                            .pointerInput(Unit) { detectTapGestures { } },
                        contentAlignment = Alignment.Center,
                    ) {
                        GlassPanel(shape = RoundedCornerShape(24.dp), strong = true, padding = 28.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Palette.FocusCyan)
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "جاري استعادة الاشتراك والمفضلة من السحابة...",
                                    color = Palette.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                container.syncCoordinator.triggerFullSync(
                    context = context,
                    forceCatalog = false,
                    reason = "login",
                )
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
        composable(Routes.SETTINGS) {
            val scope = rememberCoroutineScope()
            SettingsScreen(
                onBack = back,
                onOpenCloudLogin = { nav.navigate(Routes.CLOUD_LOGIN) },
                onLoggedOut = {
                    container.stopAutoSync()
                    com.saydlawy.sahab.data.CatalogSyncWorker.cancelPeriodic(context)
                    container.mergeCoordinator.reset()
                    container.authManager.signOut(context)
                    container.repo.setAccount(null)
                    scope.launch {
                        container.clearAllLocalData(clearPrefsAccount = true)
                        nav.navigate(Routes.CLOUD_LOGIN) {
                            popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
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

@Composable
private fun NotificationPermissionPrompt(
    currentRoute: String?,
    isTv: Boolean,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (currentRoute != Routes.HOME || isTv) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPrompt by remember { mutableStateOf(false) }

    val markPrompted: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            context.applicationContext
                .getSharedPreferences("sahab_fcm_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("prompted_notifications", true)
                .apply()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        markPrompted()
        showPrompt = false
    }

    LaunchedEffect(currentRoute) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            val alreadyPrompted = withContext(Dispatchers.IO) {
                context.applicationContext
                    .getSharedPreferences("sahab_fcm_prefs", Context.MODE_PRIVATE)
                    .getBoolean("prompted_notifications", false)
            }
            if (!alreadyPrompted) {
                showPrompt = true
            }
        }
    }

    if (showPrompt) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                markPrompted()
                showPrompt = false
            },
        ) {
            GlassPanel(shape = RoundedCornerShape(22.dp), strong = true, padding = 22.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "تفعيل الإشعارات",
                        color = Palette.Text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "هل ترغب في استقبال تنبيهات المباريات والأفلام والتحديثات الجديدة من سحاب؟",
                        color = Palette.TextDim,
                        fontSize = 14.sp,
                    )
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                    ) {
                        com.saydlawy.sahab.ui.components.GlassButton(
                            text = "تفعيل",
                            primary = true,
                            onClick = {
                                showPrompt = false
                                runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            },
                        )
                        com.saydlawy.sahab.ui.components.GlassButton(
                            text = "لاحقاً",
                            onClick = {
                                markPrompted()
                                showPrompt = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncPinUnlockDialog(
    container: AppContainer,
    nav: NavHostController,
) {
    val needsPin by container.syncCoordinator.needsSyncPinPrompt.collectAsStateWithLifecycle()
    if (!needsPin) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var unlocking by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { container.syncCoordinator.dismissSyncPinPrompt() },
    ) {
        GlassPanel(shape = RoundedCornerShape(22.dp), strong = true, padding = 22.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "استعادة الاشتراك المشفر",
                    color = Palette.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "اشتراكك السحابي محمي برمز مزامنة (Sync PIN). أدخل الرمز لفك التشفير واستعادته:",
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                )
                com.saydlawy.sahab.ui.components.GlassTextField(
                    value = pin,
                    onValueChange = { pin = it; errorMsg = null },
                    placeholder = "رمز المزامنة (Sync PIN)",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    password = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = Palette.Danger, fontSize = 12.sp)
                }
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    com.saydlawy.sahab.ui.components.GlassButton(
                        text = if (unlocking) "جاري الفك..." else "استعادة",
                        primary = true,
                        onClick = {
                            if (unlocking) return@GlassButton
                            unlocking = true
                            scope.launch {
                                when (container.syncCoordinator.submitRestorePin(context, pin)) {
                                    is com.saydlawy.sahab.data.auth.CloudAccountRestoreResult.Restored -> {
                                        nav.navigate(Routes.HOME) {
                                            popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                    is com.saydlawy.sahab.data.auth.CloudAccountRestoreResult.WrongPin -> {
                                        errorMsg = "رمز المزامنة غير صحيح"
                                    }
                                    else -> {
                                        errorMsg = "تعذر العثور على اشتراك بهذا الرمز"
                                    }
                                }
                                unlocking = false
                            }
                        },
                    )
                    com.saydlawy.sahab.ui.components.GlassButton(
                        text = "لاحقاً",
                        onClick = { container.syncCoordinator.dismissSyncPinPrompt() },
                    )
                }
            }
        }
    }
}

@Composable
private fun RootSyncStatusOverlay(
    container: AppContainer,
    onPlayer: Boolean,
) {
    val repoSyncing by container.repo.syncing.collectAsStateWithLifecycle()
    val catalogSyncing by container.catalogSync.syncing.collectAsStateWithLifecycle()
    val firebaseSyncing by container.catalogSync.firebaseSyncing.collectAsStateWithLifecycle()
    val firebaseSyncMessage by container.catalogSync.firebaseSyncMessage.collectAsStateWithLifecycle()
    val channelSyncing by container.channelSync.syncing.collectAsStateWithLifecycle()
    val epgSyncing by container.epgSync.syncing.collectAsStateWithLifecycle()
    val switchingAccount by container.switchingAccount.collectAsStateWithLifecycle()
    val syncing = repoSyncing || catalogSyncing || firebaseSyncing || channelSyncing || epgSyncing || switchingAccount

    if (switchingAccount && !onPlayer) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            GlassPanel(shape = RoundedCornerShape(24.dp), strong = true, padding = 28.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Palette.FocusCyan)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "جاري تبديل الاشتراك وتحديث القنوات والأفلام...",
                        color = Palette.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        SyncOverlay(
            visible = syncing && !switchingAccount && !onPlayer,
            message = firebaseSyncMessage ?: "جارٍ تحديث البيانات...",
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun GithubUpdatePrompt(
    container: AppContainer,
    visible: Boolean = true,
) {
    val context = LocalContext.current
    var pendingUpdate by remember { mutableStateOf<com.saydlawy.sahab.data.GithubReleaseUpdate?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5_000L)
        pendingUpdate = com.saydlawy.sahab.data.GithubReleaseUpdater.checkForUpdate(container.client)
    }

    if (!visible) return
    val update = pendingUpdate ?: return
    androidx.compose.ui.window.Dialog(onDismissRequest = { pendingUpdate = null }) {
        com.saydlawy.sahab.ui.components.GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            strong = true,
            padding = 20.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "تحديث جديد متاح (${update.versionCode})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    "يتوفر إصدار أحدث من سحاب (${update.title}). يمكنك تحميله الآن أو لاحقاً.",
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                )
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.saydlawy.sahab.ui.components.GlassButton(
                        text = "تحديث الآن",
                        primary = true,
                        onClick = {
                            val target = update.apkUrl.ifBlank { update.htmlUrl }
                            if (com.saydlawy.sahab.data.GithubReleaseUpdater.isTrustedReleaseUrl(target)) {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(target),
                                        ),
                                    )
                                }
                            }
                            pendingUpdate = null
                        },
                    )
                    com.saydlawy.sahab.ui.components.GlassButton(
                        text = "لاحقاً",
                        onClick = { pendingUpdate = null },
                    )
                }
            }
        }
    }
}
