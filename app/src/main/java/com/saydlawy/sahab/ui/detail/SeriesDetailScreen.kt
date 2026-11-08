package com.saydlawy.sahab.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.Chip
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SeriesDetailViewModel(private val container: AppContainer, private val id: String) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState<SeriesDetails>())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { DetailUiState(loading = true) }
            try {
                val details = container.repo.seriesDetails(id)
                _state.update { DetailUiState(loading = false, data = details) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { DetailUiState(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun toggleFavorite(details: SeriesDetails) {
        viewModelScope.launch {
            container.prefs.toggleFavorite(FavItem(Kind.SERIES, details.id, details.name, details.cover))
        }
    }
}

@Composable
fun SeriesDetailScreen(id: String, onBack: () -> Unit, onPlay: (PlayRequest) -> Unit) {
    val vm = appViewModel { SeriesDetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalContainer.current
    val favorites by container.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val recents by container.prefs.recents.collectAsStateWithLifecycle(initialValue = emptyList())

    Box(Modifier.fillMaxSize()) {
        val details = state.data
        when {
            state.loading -> {
                LoadingBox()
                BackButton(onBack)
            }
            details == null -> {
                MessageBox(state.error ?: Ar.errUnknown, actionLabel = Ar.retry, onAction = vm::load)
                BackButton(onBack)
            }
            else -> {
                RemoteImage(details.backdrop.ifBlank { details.cover }, Modifier.fillMaxSize().alpha(0.28f))
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Bg.copy(alpha = 0.95f)))),
                )
                SeriesContent(
                    details = details,
                    recents = recents,
                    isFavorite = favorites.any { it.kind == Kind.SERIES && it.id == details.id },
                    onToggleFavorite = { vm.toggleFavorite(details) },
                    onPlay = onPlay,
                )
                BackButton(onBack)
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
    onToggleFavorite: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
) {
    val seasons = details.seasons
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    val season = seasons.getOrNull(selectedSeasonIndex) ?: seasons.firstOrNull()
    val visibleEpisodes = details.episodes.filter { it.season == season }
    val queue = remember(details) { details.episodes.map { playItem(details, it) } }
    val progress = remember(recents) {
        recents.filter { it.kind == Kind.EPISODE && it.groupId == details.id }.associateBy { it.id }
    }
    val lastWatched = recents.firstOrNull { it.kind == Kind.EPISODE && it.groupId == details.id }
    val resumeIndex = lastWatched?.let { last -> details.episodes.indexOfFirst { it.id == last.id } } ?: -1

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(54.dp)) }
            item {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Cover(details.cover, Modifier.width(220.dp))
                        Header(details, isFavorite, onToggleFavorite, resumeIndex, queue, onPlay, Modifier.weight(1f))
                    }
                } else {
                    Column {
                        Cover(details.cover, Modifier.width(170.dp).align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(16.dp))
                        Header(details, isFavorite, onToggleFavorite, resumeIndex, queue, onPlay, Modifier.fillMaxWidth())
                    }
                }
            }
            if (seasons.size > 1) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(seasons.size) { index ->
                            Chip(
                                text = Ar.season(seasons[index]),
                                selected = index == selectedSeasonIndex,
                                onClick = { selectedSeasonIndex = index },
                            )
                        }
                    }
                }
            }
            if (details.episodes.isEmpty()) {
                item { Text(Ar.noEpisodes, fontSize = 17.sp, color = Palette.TextDim) }
            }
            items(visibleEpisodes, key = { it.id }) { episode ->
                EpisodeRow(
                    episode = episode,
                    saved = progress[episode.id],
                    onClick = {
                        val index = details.episodes.indexOfFirst { it.id == episode.id }
                        if (index >= 0) onPlay(PlayRequest(queue, index))
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Header(
    details: SeriesDetails,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    resumeIndex: Int,
    queue: List<PlayItem>,
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(details.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        MetaLine(listOf(details.year, details.genre).filter { it.isNotBlank() }.joinToString("  •  "), details.rating)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (queue.isNotEmpty()) {
                val resumeItem = queue.getOrNull(resumeIndex)
                GlassButton(
                    text = if (resumeItem != null) Ar.resumeEpisode else Ar.playFirst,
                    onClick = { onPlay(PlayRequest(queue, resumeIndex.coerceAtLeast(0))) },
                    icon = Ic.Play,
                    primary = true,
                    autoFocus = true,
                )
            }
            GlassIconButton(
                icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                description = Ar.favorites,
                onClick = onToggleFavorite,
                tint = if (isFavorite) Palette.Pink else Palette.Text,
            )
        }
        if (details.plot.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(details.plot, fontSize = 17.sp, lineHeight = 27.sp, color = Palette.Text.copy(alpha = 0.92f))
        }
        if (details.cast.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(Ar.cast, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Cyan)
            Text(details.cast, fontSize = 15.sp, color = Palette.TextDim)
        }
    }
}

@Composable
private fun Cover(url: String, modifier: Modifier) {
    RemoteImage(
        url = url,
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(22.dp)),
    )
}

@Composable
private fun EpisodeRow(episode: Episode, saved: RecentItem?, onClick: () -> Unit) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(
                url = episode.image,
                modifier = Modifier
                    .width(124.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = Ar.episodeNumber(episode.number),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Palette.Cyan,
                )
                Text(
                    text = episode.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode.duration.isNotBlank()) {
                    Text(episode.duration, fontSize = 14.sp, color = Palette.TextDim)
                }
                if (saved != null && saved.durationMs > 0) {
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(saved.positionMs.toFloat() / saved.durationMs)
                }
            }
            Box(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(Modifier.padding(20.dp)) {
        GlassIconButton(Ic.Back, Ar.back, onBack)
    }
}
