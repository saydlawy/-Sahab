package com.saydlawy.sahab.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatDuration
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    fun load() {
        viewModelScope.launch {
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
                val backdrop = details.backdrop.ifBlank { details.image }
                RemoteImage(backdrop, Modifier.fillMaxSize().alpha(0.30f))
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Bg.copy(alpha = 0.95f)))),
                )
                val isFavorite = favorites.any { it.kind == Kind.MOVIE && it.id == details.id }
                val resume = recents.firstOrNull { it.kind == Kind.MOVIE && it.id == details.id }
                val resumeMs = resume?.positionMs ?: 0L
                val item = PlayItem(Kind.MOVIE, details.id, details.name, icon = details.image, ext = details.ext)

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 720.dp
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                    ) {
                        Spacer(Modifier.height(64.dp))
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                Poster(details.image, Modifier.width(240.dp))
                                Info(details, resumeMs, isFavorite, item, vm::toggleFavorite, onPlay, Modifier.weight(1f))
                            }
                        } else {
                            Poster(details.image, Modifier.width(190.dp).align(androidx.compose.ui.Alignment.CenterHorizontally))
                            Spacer(Modifier.height(18.dp))
                            Info(details, resumeMs, isFavorite, item, vm::toggleFavorite, onPlay, Modifier.fillMaxWidth())
                        }
                    }
                }
                BackButton(onBack)
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(Modifier.padding(20.dp)) {
        GlassIconButton(Ic.Back, Ar.back, onBack)
    }
}

@Composable
private fun Poster(url: String, modifier: Modifier) {
    RemoteImage(
        url = url,
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(22.dp)),
    )
}

@Composable
private fun Info(
    details: MovieDetails,
    resumeMs: Long,
    isFavorite: Boolean,
    item: PlayItem,
    onToggleFavorite: (MovieDetails) -> Unit,
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(details.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        MetaLine(listOf(details.year, details.duration, details.genre).filter { it.isNotBlank() }.joinToString("  •  "), details.rating)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val canResume = resumeMs > 10_000
            GlassButton(
                text = if (canResume) Ar.resumeFrom(formatDuration(resumeMs)) else Ar.play,
                onClick = { onPlay(PlayRequest(listOf(item), 0)) },
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
                onClick = { onToggleFavorite(details) },
                tint = if (isFavorite) Palette.Pink else Palette.Text,
            )
        }
        if (details.plot.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(details.plot, fontSize = 17.sp, lineHeight = 27.sp, color = Palette.Text.copy(alpha = 0.92f))
        }
        if (details.cast.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(Ar.cast, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Cyan)
            Text(details.cast, fontSize = 15.sp, color = Palette.TextDim)
        }
        if (details.director.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(Ar.director, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Cyan)
            Text(details.director, fontSize = 15.sp, color = Palette.TextDim)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun MetaLine(text: String, rating: String) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (rating.isNotBlank()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    Ic.Star,
                    contentDescription = null,
                    tint = Palette.Peach,
                    modifier = Modifier.height(20.dp).width(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(rating, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Peach)
            }
        }
        if (text.isNotBlank()) {
            Text(text, fontSize = 16.sp, color = Palette.TextDim)
        }
    }
}
