package com.saydlawy.sahab.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Episode
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.SeriesDetails
import com.saydlawy.sahab.data.forAccount
import com.saydlawy.sahab.data.isFav
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.Chip
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import androidx.compose.runtime.LaunchedEffect
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization
import com.saydlawy.sahab.ui.theme.isVisible
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SeriesDetailScreen(id: String, onBack: () -> Unit, onPlay: (PlayRequest) -> Unit) {
    val vm = appViewModel(key = "series_$id") { SeriesDetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val aiState by vm.aiState.collectAsStateWithLifecycle()
    val container = LocalContainer.current
    val activeAccountId by container.prefs.activeAccountId.collectAsStateWithLifecycle(
        initialValue = container.prefs.currentActiveAccountId(),
    )
    val favorites by container.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val allRecents by container.prefs.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val recents = remember(allRecents, activeAccountId) { allRecents.forAccount(activeAccountId) }

    Box(Modifier.fillMaxSize()) {
        val details = state.data
        if (details != null) {
            RemoteImage(details.backdrop.ifBlank { details.cover }, Modifier.fillMaxSize().alpha(0.22f))
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Palette.DeepBlue.copy(alpha = 0.55f),
                                Palette.DeepBlue.copy(alpha = 0.88f),
                                Palette.DeepBlue.copy(alpha = 0.98f),
                            ),
                        ),
                    ),
            )
        }

        Column(Modifier.fillMaxSize()) {
            SahabTopBar(current = Routes.SERIES, onBack = onBack)

            when {
                state.loading -> LoadingBox()
                details == null -> MessageBox(state.error ?: Ar.errUnknown, actionLabel = Ar.retry, onAction = vm::load)
                else -> {
                    val isFavorite = favorites.isFav(activeAccountId, Kind.SERIES, details.id)
                    SeriesContent(
                        details = details,
                        recents = recents,
                        isFavorite = isFavorite,
                        aiState = aiState,
                        onGenerateStory = { vm.generateAiStory() },
                        onToggleFavorite = { vm.toggleFavorite(details) },
                        onRefresh = { vm.refreshMetadata() },
                        onPlay = onPlay,
                    )
                }
            }
        }
    }
}

private fun playItem(details: SeriesDetails, episode: Episode) = PlayItem(
    kind = Kind.EPISODE,
    id = episode.id,
    title = details.name,
    subtitle = Ar.seasonEpisode(episode.season, episode.number),
    icon = episode.image.ifBlank { details.cover },
    ext = episode.ext,
    groupId = details.id,
)

@Composable
private fun SeriesContent(
    details: SeriesDetails,
    recents: List<RecentItem>,
    isFavorite: Boolean,
    aiState: com.saydlawy.sahab.data.ai.AiGenerationState,
    onGenerateStory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
) {
    val focusNav = LocalFocusNavigation.current
    val screenKey = remember(details.id) { "series/${details.id}" }
    val seasons = details.seasons
    var selectedSeasonIndex by remember(details.id) {
        mutableIntStateOf(
            focusNav.getSelectedSeason(details.id).coerceIn(0, (seasons.size - 1).coerceAtLeast(0))
        )
    }
    LaunchedEffect(selectedSeasonIndex) {
        focusNav.saveSelectedSeason(details.id, selectedSeasonIndex)
    }
    val season = seasons.getOrNull(selectedSeasonIndex) ?: seasons.firstOrNull()
    val visibleEpisodes = details.episodes.filter { it.season == season }
    val queue = remember(details) { details.episodes.map { playItem(details, it) } }
    val episodeIds = remember(details) { details.episodes.map { it.id }.toSet() }
    val progress = remember(recents, details.id, episodeIds) {
        recents.filter {
            it.kind == Kind.EPISODE && (it.groupId == details.id || it.id in episodeIds)
        }.associateBy { it.id }
    }
    val lastWatched = remember(recents, details.id, episodeIds) {
        recents.firstOrNull {
            it.kind == Kind.EPISODE && (it.groupId == details.id || it.id in episodeIds)
        }
    }
    val resumeIndex = lastWatched?.let { last -> details.episodes.indexOfFirst { it.id == last.id } } ?: -1
    val resumePosMs = lastWatched?.positionMs ?: -1L
    val episodesState = rememberRestorableLazyListState("series_episodes_${details.id}_$selectedSeasonIndex")
    val seasonsRowState = rememberRestorableLazyListState("series_seasons_${details.id}")

    val displayMode = LocalDisplayMode.current
    val hPad = when (displayMode) {
        DisplayMode.TABLET_OR_TV -> 36.dp
        DisplayMode.PHONE_LANDSCAPE -> 20.dp
        DisplayMode.PHONE_PORTRAIT -> 16.dp
    }

    Box(Modifier.fillMaxSize()) {

        LazyColumn(
            state = episodesState,
            modifier = Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(horizontal = hPad, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                when (displayMode) {
                    DisplayMode.TABLET_OR_TV -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            SeriesCoverFrame(details.cover, details.name, Modifier.width(185.dp))
                            SeriesInfoPanel(
                                details = details,
                                resumeIndex = resumeIndex,
                                resumePositionMs = resumePosMs,
                                queue = queue,
                                isFavorite = isFavorite,
                                aiState = aiState,
                                onGenerateStory = onGenerateStory,
                                onToggleFavorite = onToggleFavorite,
                                onRefresh = onRefresh,
                                onPlay = onPlay,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    DisplayMode.PHONE_LANDSCAPE -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            SeriesCoverFrame(details.cover, details.name, Modifier.width(140.dp))
                            SeriesInfoPanel(
                                details = details,
                                resumeIndex = resumeIndex,
                                resumePositionMs = resumePosMs,
                                queue = queue,
                                isFavorite = isFavorite,
                                aiState = aiState,
                                onGenerateStory = onGenerateStory,
                                onToggleFavorite = onToggleFavorite,
                                onRefresh = onRefresh,
                                onPlay = onPlay,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    DisplayMode.PHONE_PORTRAIT -> {
                        Column {
                            SeriesCoverFrame(
                                details.cover,
                                details.name,
                                Modifier.width(160.dp).align(Alignment.CenterHorizontally),
                            )
                            Spacer(Modifier.height(16.dp))
                            SeriesInfoPanel(
                                details = details,
                                resumeIndex = resumeIndex,
                                resumePositionMs = resumePosMs,
                                queue = queue,
                                isFavorite = isFavorite,
                                aiState = aiState,
                                onGenerateStory = onGenerateStory,
                                onToggleFavorite = onToggleFavorite,
                                onRefresh = onRefresh,
                                onPlay = onPlay,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (seasons.size > 1) {
                item {
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        state = seasonsRowState,
                        modifier = Modifier.focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        items(seasons.size, key = { index -> seasons[index] }) { index ->
                            Chip(
                                text = Ar.season(seasons[index]),
                                selected = index == selectedSeasonIndex,
                                onClick = { selectedSeasonIndex = index },
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DotTitle(
                        text = if (season != null) "${Ar.season(season)} · ${visibleEpisodes.size} حلقة" else Ar.seriesSub,
                        dot = Palette.Glow,
                        fontSize = 16.sp,
                    )
                }
            }

            if (details.episodes.isEmpty()) {
                item { Text(Ar.noEpisodes, fontSize = 16.sp, color = Palette.TextDim) }
            }

            items(visibleEpisodes, key = { it.id }) { episode ->
                EpisodeRow(
                    episode = episode,
                    coverFallback = details.cover,
                    saved = progress[episode.id],
                    screenKey = screenKey,
                    onClick = {
                        val index = details.episodes.indexOfFirst { it.id == episode.id }
                        if (index >= 0) onPlay(PlayRequest(queue, index))
                    },
                )
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun SeriesCoverFrame(url: String, title: String, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .shadow(20.dp, shape, ambientColor = Palette.Royal, spotColor = Palette.Glow)
            .clip(shape)
            .background(Palette.Glass)
            .border(1.5.dp, Palette.Glow.copy(alpha = 0.45f), shape)
            .padding(6.dp),
    ) {
        RemoteImage(
            url = url,
            fallbackText = title,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp)),
        )
    }
}

@Composable
private fun SeriesInfoPanel(
    details: SeriesDetails,
    resumeIndex: Int,
    resumePositionMs: Long,
    queue: List<PlayItem>,
    isFavorite: Boolean,
    aiState: com.saydlawy.sahab.data.ai.AiGenerationState,
    onGenerateStory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeCustomization = LocalThemeCustomization.current
    val showMetadata = themeCustomization.isVisible("details_metadata")
    val showActions = themeCustomization.isVisible("details_actions")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showMetadata) {
            Text(
                details.name,
                style = TextStyle(brush = Palette.BrandText, fontSize = 30.sp, fontWeight = FontWeight.Bold),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            qualityOf(details.name)?.let { q ->
                Tag(q, if (q == "4K") Palette.Gold else Palette.Glow)
            }
            if (details.year.isNotBlank()) {
                Tag(details.year, Palette.Royal)
            }
            if (details.seasons.isNotEmpty()) {
                Text("${details.seasons.size} مواسم", fontSize = 14.sp, color = Palette.TextDim)
            }
            if (details.rating.isNotBlank()) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.Gold.copy(alpha = 0.16f))
                        .border(1.dp, Palette.Gold.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.Star, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(details.rating, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Gold)
                }
            }
            }

            if (details.genre.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    details.genre.split(",", "،", "/").map { it.trim() }.filter { it.isNotBlank() }.take(4).forEach { g ->
                        Tag(g, Palette.Glow, fontSize = 12.sp)
                    }
                }
            }
        }

        if (showActions) Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (queue.isNotEmpty()) {
                val resumeItem = queue.getOrNull(resumeIndex)
                GlassButton(
                    text = if (resumeItem != null) Ar.resumeEpisode else Ar.playFirst,
                    onClick = {
                        onPlay(
                            PlayRequest(
                                items = queue,
                                startIndex = resumeIndex.coerceAtLeast(0),
                                fromStart = false,
                                initialPositionMs = if (resumeItem != null) resumePositionMs else -1L,
                            ),
                        )
                    },
                    icon = Ic.Play,
                    primary = true,
                    autoFocus = false,
                    modifier = Modifier.restorableFocus(
                        screenKey = "series/${details.id}",
                        itemKey = "series_play_btn",
                        isDefaultEntry = true,
                    ),
                )
            }
            GlassIconButton(
                icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                description = Ar.favorites,
                onClick = onToggleFavorite,
                size = 54.dp,
                selected = isFavorite,
                tint = if (isFavorite) Palette.Live else Palette.Glow,
            )
            GlassIconButton(
                icon = Ic.Refresh,
                description = Ar.refreshData,
                onClick = onRefresh,
                size = 54.dp,
                tint = Palette.FocusCyan,
            )
        }

        if (showMetadata) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                padding = 18.dp,
            ) {
                AiStoryPlotSection(
                    plot = details.plot,
                    emptyLabel = "لا توجد قصة متاحة لهذا المسلسل.",
                    aiState = aiState,
                    onGenerateStory = onGenerateStory,
                    plotFontSizeSp = 14,
                    plotLineHeightSp = 23,
                )
                if (details.cast.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = Ar.cast,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Palette.Glow,
                            modifier = Modifier.width(90.dp),
                        )
                        Text(
                            text = details.cast,
                            fontSize = 14.sp,
                            color = Palette.Text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    coverFallback: String,
    saved: RecentItem?,
    screenKey: String = "",
    onClick: () -> Unit,
) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (screenKey.isNotBlank()) {
                    Modifier.restorableFocus(
                        screenKey = screenKey,
                        itemKey = "ep_${episode.id}",
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(18.dp),
        focusScale = 1.02f,
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(
                url = episode.image.ifBlank { coverFallback },
                fallbackText = episode.title,
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(Ar.episodeNumber(episode.number), Palette.Glow, fontSize = 10.sp)
                    if (episode.duration.isNotBlank()) {
                        Text(episode.duration, fontSize = 12.sp, color = Palette.TextDim)
                    }
                }
                Text(
                    text = episode.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (focused) Palette.Text else Palette.Glow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode.plot.isNotBlank()) {
                    Text(
                        text = episode.plot,
                        fontSize = 12.sp,
                        color = Palette.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (saved != null && saved.durationMs > 0) {
                    Spacer(Modifier.height(4.dp))
                    ProgressLine(saved.positionMs.toFloat() / saved.durationMs, height = 4.dp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (focused) Palette.Royal else Palette.Royal.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Play, contentDescription = null, tint = Palette.Text, modifier = Modifier.size(20.dp))
            }
        }
    }
}
