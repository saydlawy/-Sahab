package com.saydlawy.sahab.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.MovieDetails
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassButton
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
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatDuration
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DetailUiState<T>(
    val loading: Boolean = true,
    val data: T? = null,
    val error: String? = null,
)

class MovieDetailViewModel(private val container: AppContainer, private val id: String) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState<MovieDetails>())
    val state = _state.asStateFlow()

    init {
        load()
    }

    private fun CatalogMovieEntity.toMovieDetails() = MovieDetails(
        id = novaStreamId,
        name = titleAr.ifBlank { titleEn },
        image = posterUrl,
        backdrop = backdropUrl.ifBlank { posterUrl },
        plot = plot,
        cast = cast,
        director = director,
        genre = genre,
        year = year,
        duration = runtime,
        rating = rating,
        ext = containerExtension,
    )

    fun load() {
        viewModelScope.launch {
            // 1. Instant: read from Room
            val fromRoom = withContext(Dispatchers.IO) {
                runCatching { container.catalogDb.movieDao().findByStreamId(id) }.getOrNull()
            }

            if (fromRoom != null) {
                _state.update { DetailUiState(loading = false, data = fromRoom.toMovieDetails()) }

                // 2. Background enrichment if needed
                if (fromRoom.needsEnrichment == 1) {
                    try {
                        container.catalogSync.enrichOne(container.repo, container.tmdb, id)
                        val updated = withContext(Dispatchers.IO) {
                            runCatching { container.catalogDb.movieDao().findByStreamId(id) }.getOrNull()
                        }
                        if (updated != null) {
                            _state.update { DetailUiState(loading = false, data = updated.toMovieDetails()) }
                        }
                    } catch (_: Exception) {
                        // Keep showing basic data on enrichment failure
                    }
                }
                return@launch
            }

            // 3. Fallback: movie not yet in Room — use original repo path
            _state.update { DetailUiState(loading = true) }
            try {
                val details = container.repo.movieDetails(id)
                _state.update { DetailUiState(loading = false, data = details) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { DetailUiState(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun toggleFavorite(details: MovieDetails) {
        viewModelScope.launch {
            container.prefs.toggleFavorite(FavItem(Kind.MOVIE, details.id, details.name, details.image, details.ext))
        }
    }
}

@Composable
fun MovieDetailScreen(id: String, onBack: () -> Unit, onPlay: (PlayRequest) -> Unit) {
    val vm = appViewModel { MovieDetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalContainer.current
    val favorites by container.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val recents by container.prefs.recents.collectAsStateWithLifecycle(initialValue = emptyList())

    Box(Modifier.fillMaxSize()) {
        val details = state.data
        if (details != null) {
            val backdrop = details.backdrop.ifBlank { details.image }
            RemoteImage(backdrop, Modifier.fillMaxSize().alpha(0.22f))
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
            SahabTopBar(current = Routes.MOVIES, onBack = onBack)

            when {
                state.loading -> LoadingBox()
                details == null -> MessageBox(state.error ?: Ar.errUnknown, actionLabel = Ar.retry, onAction = vm::load)
                else -> {
                    val isFavorite = favorites.any { it.kind == Kind.MOVIE && it.id == details.id }
                    val resume = recents.firstOrNull { it.kind == Kind.MOVIE && it.id == details.id }
                    val resumeMs = resume?.positionMs ?: 0L
                    val durationMs = resume?.durationMs ?: 0L
                    val item = PlayItem(Kind.MOVIE, details.id, details.name, icon = details.image, ext = details.ext)

                    val displayMode = LocalDisplayMode.current
                    val hPad = when (displayMode) {
                        DisplayMode.TABLET_OR_TV -> 36.dp
                        DisplayMode.PHONE_LANDSCAPE -> 20.dp
                        DisplayMode.PHONE_PORTRAIT -> 16.dp
                    }

                    Box(Modifier.fillMaxSize()) {

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = hPad, vertical = 14.dp),
                        ) {
                            when (displayMode) {
                                DisplayMode.TABLET_OR_TV -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        MoviePosterFrame(details.image, details.name, Modifier.width(190.dp))
                                        MovieInfoPanel(
                                            details = details,
                                            resumeMs = resumeMs,
                                            durationMs = durationMs,
                                            isFavorite = isFavorite,
                                            onToggleFavorite = { vm.toggleFavorite(details) },
                                            item = item,
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
                                        MoviePosterFrame(details.image, details.name, Modifier.width(140.dp))
                                        MovieInfoPanel(
                                            details = details,
                                            resumeMs = resumeMs,
                                            durationMs = durationMs,
                                            isFavorite = isFavorite,
                                            onToggleFavorite = { vm.toggleFavorite(details) },
                                            item = item,
                                            onPlay = onPlay,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                DisplayMode.PHONE_PORTRAIT -> {
                                    MoviePosterFrame(
                                        details.image,
                                        details.name,
                                        Modifier.width(160.dp).align(Alignment.CenterHorizontally),
                                    )
                                    Spacer(Modifier.height(18.dp))
                                    MovieInfoPanel(
                                        details = details,
                                        resumeMs = resumeMs,
                                        durationMs = durationMs,
                                        isFavorite = isFavorite,
                                        onToggleFavorite = { vm.toggleFavorite(details) },
                                        item = item,
                                        onPlay = onPlay,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            Spacer(Modifier.height(48.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviePosterFrame(url: String, title: String, modifier: Modifier) {
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
private fun MovieInfoPanel(
    details: MovieDetails,
    resumeMs: Long,
    durationMs: Long,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    item: PlayItem,
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            details.name,
            style = TextStyle(brush = Palette.BrandText, fontSize = 32.sp, fontWeight = FontWeight.Bold),
        )

        // Metadata badges row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val q = qualityOf(details.name) ?: "FHD"
            Tag(q, if (q == "4K") Palette.Gold else Palette.Glow)
            if (details.year.isNotBlank()) {
                Tag(details.year, Palette.Royal)
            }
            if (details.duration.isNotBlank()) {
                Text(details.duration, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.TextDim)
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
            if (details.ext.isNotBlank()) {
                Tag(details.ext.uppercase(), Palette.TextDim)
            }
        }

        // Genre tags
        if (details.genre.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                details.genre.split(",", "،", "/").map { it.trim() }.filter { it.isNotBlank() }.take(4).forEach { g ->
                    Tag(g, Palette.Glow, fontSize = 12.sp)
                }
            }
        }

        if (resumeMs > 10_000 && durationMs > 0) {
            ProgressLine((resumeMs.toFloat() / durationMs).coerceIn(0f, 1f), Modifier.fillMaxWidth(0.6f), height = 5.dp)
        }

        // Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val canResume = resumeMs > 10_000
            GlassButton(
                text = if (canResume) Ar.resumeFrom(formatDuration(resumeMs)) else Ar.play,
                onClick = { onPlay(PlayRequest(listOf(item), 0, initialPositionMs = resumeMs)) },
                icon = Ic.Play,
                primary = true,
                autoFocus = true,
            )
            if (canResume) {
                GlassButton(Ar.fromStart, { onPlay(PlayRequest(listOf(item), 0, fromStart = true)) })
            }
            GlassIconButton(
                icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                description = Ar.favorites,
                onClick = onToggleFavorite,
                size = 54.dp,
                selected = isFavorite,
                tint = if (isFavorite) Palette.Live else Palette.Glow,
            )
        }

        // Plot & cast inside a glass panel
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            padding = 20.dp,
        ) {
            if (details.plot.isNotBlank()) {
                DotTitle(Ar.plot, Palette.Gold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    details.plot,
                    fontSize = 15.sp,
                    lineHeight = 25.sp,
                    color = Palette.Text.copy(alpha = 0.90f),
                )
                Spacer(Modifier.height(18.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (details.director.isNotBlank()) {
                    DetailRow(Ar.director, details.director)
                }
                if (details.cast.isNotBlank()) {
                    DetailRow(Ar.cast, details.cast)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Glow,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = Palette.Text,
            modifier = Modifier.weight(1f),
        )
    }
}
