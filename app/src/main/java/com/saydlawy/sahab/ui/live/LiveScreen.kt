package com.saydlawy.sahab.ui.live

import com.saydlawy.sahab.data.channel.EpgProgramEntity
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Program
import com.saydlawy.sahab.data.channel.ChannelListEntity
import com.saydlawy.sahab.data.forAccount
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.live.custom.ChannelManageContextSheet
import com.saydlawy.sahab.ui.live.custom.CustomListChannelPickerDialog
import com.saydlawy.sahab.ui.live.custom.CustomListsManagerDialog
import com.saydlawy.sahab.ui.LocalAppScale
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.Chip
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.EpgDialog
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.components.SideNavItem
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.components.rememberNow
import com.saydlawy.sahab.ui.player.SahabPlayer
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization
import com.saydlawy.sahab.ui.theme.isVisible
import com.saydlawy.sahab.util.ChannelLogoResolver
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatClock
import com.saydlawy.sahab.util.formatNowMillis
import com.saydlawy.sahab.util.normalizeForSearch
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.runtime.Immutable

internal val IdleChannelRowFill: Brush get() = SolidColor(Palette.Glass)
internal val PreviewShadeBrush: Brush get() = Brush.verticalGradient(
    listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.65f)),
)
internal val PreviewPlaceholderBrush: Brush get() = Brush.radialGradient(
    listOf(Palette.Royal.copy(alpha = 0.32f), Palette.DeepBlue, Color.Black),
)

internal const val FAV_CAT_ID = "__FAVORITES__"
internal const val ALL_CAT_ID = "__ALL__"

@Composable
fun LiveScreen(onBack: () -> Unit, onPlay: (PlayRequest) -> Unit) {
    val vm = appViewModel { LiveViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focusNav = LocalFocusNavigation.current
    val activeAccountId by LocalContainer.current.prefs.activeAccountId.collectAsStateWithLifecycle(
        initialValue = LocalContainer.current.prefs.currentActiveAccountId(),
    )
    val favorites by LocalContainer.current.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteIds = remember(favorites, activeAccountId) {
        favorites.forAccount(activeAccountId).filter { it.kind == Kind.LIVE }.map { it.id }.toSet()
    }
    var guideFor by remember { mutableStateOf<Channel?>(null) }
    var previewChannel by remember { mutableStateOf<Channel?>(null) }
    var showManageLists by remember { mutableStateOf(false) }
    var editingList by remember { mutableStateOf<ChannelListEntity?>(null) }
    var editingListIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var allChannelsForPicker by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var managingChannel by remember { mutableStateOf<Channel?>(null) }

    LaunchedEffect(state.categories.isNotEmpty()) {
        if (state.categories.isNotEmpty()) {
            val savedCat = focusNav.getSelectedCategory(Routes.LIVE)
            if (!savedCat.isNullOrBlank() && savedCat != state.selectedId) {
                vm.select(savedCat)
            }
            val savedQ = focusNav.getSearchQuery(Routes.LIVE)
            if (savedQ.isNotEmpty() && savedQ != state.query) {
                vm.onQuery(savedQ)
            }
        }
    }

    LaunchedEffect(state.selectedId) {
        focusNav.saveSelectedCategory(Routes.LIVE, state.selectedId)
    }

    LaunchedEffect(state.query) {
        focusNav.saveSearchQuery(Routes.LIVE, state.query)
    }

    val rawChannels = state.visibleChannels
    val channels = remember(rawChannels, state.selectedId, favoriteIds, state.searchResults) {
        if (state.selectedId == FAV_CAT_ID && state.searchResults == null) {
            rawChannels.filter { it.id in favoriteIds }
        } else {
            rawChannels
        }
    }

    val playScope = rememberCoroutineScope()
    var openingPlayer by remember { mutableStateOf(false) }

    val channelIdSet = remember(channels) {
        HashSet<String>(channels.size).apply { channels.forEach { add(it.id) } }
    }

    val playAt: (Int) -> Unit = { index ->
        if (!openingPlayer && channels.isNotEmpty() && index in channels.indices) {
            openingPlayer = true
            previewChannel = null
            SahabPlayer.releasePreviewPlayers()
            val snapshot = channels
            playScope.launch {
                try {
                    val items = if (snapshot.size > 300) {
                        withContext(Dispatchers.Default) {
                            snapshot.map { PlayItem(Kind.LIVE, it.id, it.name, icon = it.icon, url = it.url) }
                        }
                    } else {
                        snapshot.map { PlayItem(Kind.LIVE, it.id, it.name, icon = it.icon, url = it.url) }
                    }
                    onPlay(PlayRequest(items, index))
                } finally {
                    openingPlayer = false
                }
            }
        }
    }
    val playChannel: (Channel) -> Unit = { ch ->
        val idx = channels.indexOfFirst { it.id == ch.id }
        if (idx >= 0) {
            playAt(idx)
        } else {
            previewChannel = null
            SahabPlayer.releasePreviewPlayers()
            onPlay(PlayRequest(listOf(PlayItem(Kind.LIVE, ch.id, ch.name, icon = ch.icon, url = ch.url)), 0))
        }
    }

    val activePreview = previewChannel?.takeIf { it.id in channelIdSet }

    val handleChannelActivate: (Int) -> Unit = { idx ->
        val ch = channels.getOrNull(idx)
        if (ch != null && activePreview?.id != ch.id) {
            previewChannel = ch
        } else {
            playAt(idx)
        }
    }

    Box(Modifier.fillMaxSize()) {
        val displayMode = LocalDisplayMode.current
        when (displayMode) {
            DisplayMode.TABLET_OR_TV -> {
                WideLiveLayout(
                    state = state,
                    channels = channels,
                    favoriteIds = favoriteIds,
                    previewChannel = activePreview,
                    onPreview = { previewChannel = it },
                    onPlayAt = handleChannelActivate,
                    onPlayChannel = playChannel,
                    onToggleFavorite = vm::toggleFavorite,
                    onManageChannel = { managingChannel = it },
                    onManageLists = { showManageLists = true },
                    onGuide = { guideFor = it },
                    onSelect = vm::select,
                    onQuery = vm::onQuery,
                    onBack = onBack,
                    onRetry = { state.selectedId?.let(vm::select) },
                )
            }
            DisplayMode.PHONE_LANDSCAPE -> {
                PhoneLandscapeLiveLayout(
                    state = state,
                    channels = channels,
                    favoriteIds = favoriteIds,
                    previewChannel = activePreview,
                    onPreview = { previewChannel = it },
                    onPlayAt = handleChannelActivate,
                    onPlayChannel = playChannel,
                    onToggleFavorite = vm::toggleFavorite,
                    onManageChannel = { managingChannel = it },
                    onManageLists = { showManageLists = true },
                    onGuide = { guideFor = it },
                    onSelect = vm::select,
                    onQuery = vm::onQuery,
                    onBack = onBack,
                    onRetry = { state.selectedId?.let(vm::select) },
                )
            }
            DisplayMode.PHONE_PORTRAIT -> {
                PhonePortraitLiveLayout(
                    state = state,
                    channels = channels,
                    favoriteIds = favoriteIds,
                    previewChannel = activePreview,
                    onPreview = { previewChannel = it },
                    onPlayAt = handleChannelActivate,
                    onPlayChannel = playChannel,
                    onToggleFavorite = vm::toggleFavorite,
                    onManageChannel = { managingChannel = it },
                    onManageLists = { showManageLists = true },
                    onGuide = { guideFor = it },
                    onSelect = vm::select,
                    onQuery = vm::onQuery,
                    onBack = onBack,
                    onRetry = { state.selectedId?.let(vm::select) },
                )
            }
        }
    }

    guideFor?.let { channel ->
        EpgDialog(
            channelName = channel.name,
            channelId = channel.id,
            onDismiss = {
                guideFor = null
                focusNav.onScreenResumed(Routes.LIVE)
            }
        )
    }

    if (showManageLists) {
        CustomListsManagerDialog(
            lists = state.customLists,
            onCreateList = vm::createCustomList,
            onEditChannels = { list ->
                showManageLists = false
                editingList = list
                playScope.launch {
                    allChannelsForPicker = vm.allLiveChannels()
                    editingListIds = vm.listChannelIds(list.listId)
                }
            },
            onDeleteList = vm::deleteCustomList,
            onDismiss = {
                showManageLists = false
                focusNav.onScreenResumed(Routes.LIVE)
            },
        )
    }

    editingList?.let { list ->
        CustomListChannelPickerDialog(
            list = list,
            allChannels = allChannelsForPicker,
            initialOrderedIds = editingListIds,
            onSave = { orderedIds ->
                vm.saveListChannels(list.listId, orderedIds)
                editingList = null
                focusNav.onScreenResumed(Routes.LIVE)
            },
            onDismiss = {
                editingList = null
                focusNav.onScreenResumed(Routes.LIVE)
            },
        )
    }

    managingChannel?.let { ch ->
        ChannelManageContextSheet(
            channel = ch,
            isFavorite = ch.id in favoriteIds,
            customLists = state.customLists,
            onToggleFavorite = { vm.toggleFavorite(ch) },
            onToggleInList = { listId -> vm.toggleChannelInList(listId, ch.id) },
            onMoveUp = { vm.moveChannelOrder(ch.id, -1) },
            onMoveDown = { vm.moveChannelOrder(ch.id, 1) },
            onHideChannel = { vm.hideChannel(ch.id) },
            onDismiss = {
                managingChannel = null
                focusNav.onScreenResumed(Routes.LIVE)
            },
        )
    }
}

@Composable
internal fun WideLiveLayout(
    state: LiveUiState,
    channels: List<Channel>,
    favoriteIds: Set<String>,
    previewChannel: Channel?,
    onPreview: (Channel) -> Unit,
    onPlayAt: (Int) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onManageChannel: (Channel) -> Unit,
    onManageLists: () -> Unit,
    onGuide: (Channel) -> Unit,
    onSelect: (String) -> Unit,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val themeCustomization = LocalThemeCustomization.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize()) {
            SahabTopBar(current = Routes.LIVE, onBack = onBack)

            Row(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = appDp(24), vertical = appDp(10)),
                horizontalArrangement = Arrangement.spacedBy(appDp(22)),
            ) {
                // Column 1 (Right in RTL): Search + Categories sidebar
                Column(
                    Modifier
                        .weight(0.24f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(appDp(10)),
                ) {
                    SearchBox(
                        state.query,
                        onQuery,
                        Ar.searchChannels,
                        Modifier.fillMaxWidth(),
                    )
                    CategorySidebar(
                        categories = state.categories,
                        customLists = state.customLists,
                        selectedId = state.selectedId,
                        onSelect = onSelect,
                        onManageLists = onManageLists,
                        totalCount = state.totalCount,
                        favCount = favoriteIds.size,
                        categoryCounts = state.categoryCounts,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                // Column 2 (Center): Live Channel List — wider so full channel names fit
                ChannelColumn(
                    state = state,
                    channels = channels,
                    favoriteIds = favoriteIds,
                    previewId = previewChannel?.id,
                    onPreview = onPreview,
                    onPlayAt = onPlayAt,
                    onToggleFavorite = onToggleFavorite,
                    onManageChannel = onManageChannel,
                    onGuide = onGuide,
                    onRetry = onRetry,
                    modifier = Modifier
                        .weight(0.39f)
                        .fillMaxHeight(),
                )

                // Column 3 (Left in RTL): Video Preview & EPG Schedule
                if (themeCustomization.isVisible("live_preview")) PreviewPanel(
                    channel = previewChannel,
                    isFavorite = previewChannel?.id in favoriteIds,
                    onPlay = { previewChannel?.let(onPlayChannel) },
                    onToggleFavorite = { previewChannel?.let(onToggleFavorite) },
                    onGuide = onGuide,
                    showEpg = themeCustomization.isVisible("live_epg"),
                    modifier = Modifier
                        .weight(0.37f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
internal fun PhoneLandscapeLiveLayout(
    state: LiveUiState,
    channels: List<Channel>,
    favoriteIds: Set<String>,
    previewChannel: Channel?,
    onPreview: (Channel) -> Unit,
    onPlayAt: (Int) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onManageChannel: (Channel) -> Unit,
    onManageLists: () -> Unit,
    onGuide: (Channel) -> Unit,
    onSelect: (String) -> Unit,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val themeCustomization = LocalThemeCustomization.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize()) {
            SahabTopBar(current = Routes.LIVE, onBack = onBack)

            LazyRow(
                modifier = Modifier.padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = FAV_CAT_ID) {
                    Chip(Ar.favorites, state.selectedId == FAV_CAT_ID, { onSelect(FAV_CAT_ID) })
                }
                item(key = ALL_CAT_ID) {
                    Chip(Ar.all, state.selectedId == ALL_CAT_ID, { onSelect(ALL_CAT_ID) })
                }
                item(key = "manage_custom_lists") {
                    Chip("إدارة القوائم", false, onManageLists)
                }
                items(state.customLists, key = { "custom_${it.listId}" }) { list ->
                    val key = "$CUSTOM_LIST_PREFIX${list.listId}"
                    Chip(list.name, state.selectedId == key, { onSelect(key) })
                }
                items(state.categories, key = { it.id }) { cat ->
                    Chip(cat.name, cat.id == state.selectedId, { onSelect(cat.id) })
                }
            }

            Row(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Right pane in RTL: Search + Channel list
                Column(
                    Modifier
                        .weight(0.54f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SearchBox(
                        state.query,
                        onQuery,
                        Ar.searchChannels,
                        Modifier.fillMaxWidth(),
                    )
                    ChannelColumn(
                        state = state,
                        channels = channels,
                        favoriteIds = favoriteIds,
                        previewId = previewChannel?.id,
                        onPreview = onPreview,
                        onPlayAt = onPlayAt,
                        onToggleFavorite = onToggleFavorite,
                        onManageChannel = onManageChannel,
                        onGuide = onGuide,
                        onRetry = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                // Left pane in RTL: Compact Live Video Preview without EPG card overflow
                if (themeCustomization.isVisible("live_preview")) PreviewPanel(
                    channel = previewChannel,
                    isFavorite = previewChannel?.id in favoriteIds,
                    onPlay = { previewChannel?.let(onPlayChannel) },
                    onToggleFavorite = { previewChannel?.let(onToggleFavorite) },
                    onGuide = onGuide,
                    showEpg = false,
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
internal fun PhonePortraitLiveLayout(
    state: LiveUiState,
    channels: List<Channel>,
    favoriteIds: Set<String>,
    previewChannel: Channel?,
    onPreview: (Channel) -> Unit,
    onPlayAt: (Int) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onManageChannel: (Channel) -> Unit,
    onManageLists: () -> Unit,
    onGuide: (Channel) -> Unit,
    onSelect: (String) -> Unit,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val themeCustomization = LocalThemeCustomization.current
    Column(Modifier.fillMaxSize()) {
        SahabTopBar(current = Routes.LIVE, onBack = onBack)

        if (previewChannel != null && themeCustomization.isVisible("live_preview")) {
            PreviewPanel(
                channel = previewChannel,
                isFavorite = previewChannel.id in favoriteIds,
                onPlay = { onPlayChannel(previewChannel) },
                onToggleFavorite = { onToggleFavorite(previewChannel) },
                onGuide = onGuide,
                showEpg = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        SearchBox(
            state.query,
            onQuery,
            Ar.searchChannels,
            Modifier.padding(horizontal = appDp(12), vertical = appDp(4)),
        )
        LazyRow(
            modifier = Modifier.padding(vertical = appDp(4)),
            contentPadding = PaddingValues(horizontal = appDp(12)),
            horizontalArrangement = Arrangement.spacedBy(appDp(8)),
        ) {
            item(key = FAV_CAT_ID) {
                Chip(Ar.favorites, state.selectedId == FAV_CAT_ID, { onSelect(FAV_CAT_ID) })
            }
            item(key = ALL_CAT_ID) {
                Chip(Ar.all, state.selectedId == ALL_CAT_ID, { onSelect(ALL_CAT_ID) })
            }
            item(key = "manage_custom_lists") {
                Chip("إدارة القوائم", false, onManageLists)
            }
            items(state.customLists, key = { "custom_${it.listId}" }) { list ->
                val key = "$CUSTOM_LIST_PREFIX${list.listId}"
                Chip(list.name, state.selectedId == key, { onSelect(key) })
            }
            items(state.categories, key = { it.id }) { cat ->
                Chip(cat.name, cat.id == state.selectedId, { onSelect(cat.id) })
            }
        }
        ChannelColumn(
            state = state,
            channels = channels,
            favoriteIds = favoriteIds,
            previewId = previewChannel?.id,
            onPreview = onPreview,
            onPlayAt = onPlayAt,
            onToggleFavorite = onToggleFavorite,
            onManageChannel = onManageChannel,
            onGuide = onGuide,
            onRetry = onRetry,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = appDp(12)),
        )
    }
}

@Composable
internal fun appSp(n: Int): TextUnit = (n * LocalAppScale.current).sp

@Composable
internal fun appDp(n: Int): Dp = (n * LocalAppScale.current).dp

@Composable
internal fun appDp(n: Float): Dp = (n * LocalAppScale.current).dp
