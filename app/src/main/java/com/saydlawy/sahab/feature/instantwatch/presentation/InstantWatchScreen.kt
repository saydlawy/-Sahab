package com.saydlawy.sahab.feature.instantwatch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaItem
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaType
import com.saydlawy.sahab.feature.instantwatch.domain.GateResult
import com.saydlawy.sahab.feature.instantwatch.domain.InputResolutionResult
import com.saydlawy.sahab.feature.instantwatch.domain.OrchestrationResult
import com.saydlawy.sahab.feature.instantwatch.domain.PlaybackSessionRequest
import com.saydlawy.sahab.feature.instantwatch.local.InstantWatchHistoryEntity
import com.saydlawy.sahab.feature.instantwatch.player.ActivePlaybackSession
import com.saydlawy.sahab.feature.instantwatch.player.PlayerSessionManager
import kotlinx.coroutines.launch

private sealed class InstantWatchRoute {
    data object Home : InstantWatchRoute()
    data class SearchResults(val query: String, val items: List<InstantMediaItem>) : InstantWatchRoute()
    data class EpisodePicker(val series: InstantMediaItem) : InstantWatchRoute()
}

@Composable
fun InstantWatchScreen(
    sessionManager: PlayerSessionManager,
    onRequireLogin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val settings by sessionManager.preferences.settings.collectAsState()
    val isSignedIn by sessionManager.authGate.observeAuthSignedIn()
        .collectAsState(initial = sessionManager.authGate.hasCachedUser())
    val activeSession by sessionManager.activeSession.collectAsState()
    val recentHistory by sessionManager.orchestrator.observeRecentHistory().collectAsState(initial = emptyList())

    var route by remember { mutableStateOf<InstantWatchRoute>(InstantWatchRoute.Home) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isSignedIn, settings.enabled) {
        if (!isSignedIn || !settings.enabled) {
            sessionManager.onServiceDisabledOrSignedOut()
            route = InstantWatchRoute.Home
        }
    }

    // Level 1 UI Gate: Disabled service
    if (!settings.enabled) {
        GateMessageView(
            title = "المشاهدة الفورية معطلة",
            body = "تم تعطيل خدمة المشاهدة الفورية من الإعدادات. يمكنك إعادة تفعيلها في أي وقت.",
            primaryLabel = "رجوع",
            onPrimary = onBack,
            modifier = modifier,
        )
        return
    }

    // Level 1 UI Gate: Unauthenticated user
    if (!isSignedIn) {
        GateMessageView(
            title = "تسجيل الدخول مطلوب",
            body = "خدمة المشاهدة الفورية متاحة فقط بعد تسجيل الدخول بحسابك في سحاب.",
            primaryLabel = "تسجيل الدخول",
            onPrimary = onRequireLogin,
            secondaryLabel = "رجوع",
            onSecondary = onBack,
            modifier = modifier,
        )
        return
    }

    fun startPlayback(item: InstantMediaItem, season: Int? = null, episode: Int? = null) {
        scope.launch {
            isLoading = true
            statusMessage = null
            when (
                val res = sessionManager.orchestrator.preparePlayback(
                    PlaybackSessionRequest(item = item, seasonNumber = season, episodeNumber = episode),
                )
            ) {
                is OrchestrationResult.Ready -> {
                    sessionManager.startSession(
                        ActivePlaybackSession(
                            item = res.item,
                            seasonNumber = res.seasonNumber,
                            episodeNumber = res.episodeNumber,
                            source = res.source,
                            resumePositionMs = res.resumePositionMs,
                        ),
                    )
                }
                is OrchestrationResult.Denied -> {
                    statusMessage = when (val g = res.gate) {
                        GateResult.ServiceDisabled -> "الخدمة معطلة من الإعدادات."
                        GateResult.Unauthenticated -> {
                            onRequireLogin()
                            "يرجى تسجيل الدخول أولاً."
                        }
                        is GateResult.SessionInvalid -> g.reason
                        is GateResult.Authorized -> null
                    }
                }
                is OrchestrationResult.Error -> statusMessage = res.message
            }
            isLoading = false
        }
    }

    fun handleMediaSelected(item: InstantMediaItem) {
        if (item.mediaType == InstantMediaType.TV) {
            scope.launch {
                isLoading = true
                val detailed = sessionManager.tmdbRepository.getDetails(item.tmdbId, InstantMediaType.TV) ?: item
                isLoading = false
                route = InstantWatchRoute.EpisodePicker(detailed)
            }
        } else {
            startPlayback(item)
        }
    }

    fun performSearch() {
        val q = inputText.trim()
        scope.launch {
            isLoading = true
            statusMessage = null
            when (val res = sessionManager.inputResolver.resolve(q)) {
                is InputResolutionResult.SingleMatch -> handleMediaSelected(res.item)
                is InputResolutionResult.MultipleMatches -> route = InstantWatchRoute.SearchResults(q, res.items)
                InputResolutionResult.NoResults -> statusMessage = "لم يتم العثور على نتائج مطابقة."
                is InputResolutionResult.InvalidInput -> statusMessage = res.message
                is InputResolutionResult.Denied -> {
                    statusMessage = when (val g = res.gate) {
                        GateResult.Unauthenticated -> {
                            onRequireLogin()
                            "يرجى تسجيل الدخول أولاً."
                        }
                        GateResult.ServiceDisabled -> "الخدمة معطلة من الإعدادات."
                        is GateResult.SessionInvalid -> g.reason
                        is GateResult.Authorized -> null
                    }
                }
            }
            isLoading = false
        }
    }

    // Active Player Screen
    val currentSession = activeSession
    if (currentSession != null) {
        InstantPlayerScreen(
            session = currentSession,
            sessionManager = sessionManager,
            onProgressUpdate = { pos, dur ->
                scope.launch {
                    sessionManager.orchestrator.saveProgress(
                        item = currentSession.item,
                        seasonNumber = currentSession.seasonNumber,
                        episodeNumber = currentSession.episodeNumber,
                        positionMs = pos,
                        durationMs = dur,
                    )
                }
            },
            onClose = { sessionManager.endSession() },
            modifier = modifier,
        )
        return
    }

    when (val r = route) {
        is InstantWatchRoute.SearchResults -> {
            SearchResultsScreen(
                query = r.query,
                results = r.items,
                onSelect = { handleMediaSelected(it) },
                onBack = { route = InstantWatchRoute.Home },
                modifier = modifier,
            )
            return
        }
        is InstantWatchRoute.EpisodePicker -> {
            EpisodePickerScreen(
                series = r.series,
                onSelectEpisode = { s, e -> startPlayback(r.series, s, e) },
                onBack = { route = InstantWatchRoute.Home },
                modifier = modifier,
            )
            return
        }
        InstantWatchRoute.Home -> Unit
    }

    // Main Instant Watch Home Screen
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "المشاهدة الفورية",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "متصل",
                    color = Color(0xFF34D399),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(Color(0xFF064E3B), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            TextButton(onClick = onBack) {
                Text("رجوع", color = Color(0xFF60A5FA))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ماذا تريد أن تشاهد؟",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "اكتب اسم الفيلم أو المسلسل أو الصق رقم IMDb أو TMDB.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("اسم الفيلم أو TMDB / IMDb ID") },
            placeholder = { Text("مثال: Interstellar أو tt0816692 أو 157336") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF334155),
                focusedLabelColor = Color(0xFF60A5FA),
                unfocusedLabelColor = Color(0xFF94A3B8),
            ),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val clip = clipboard.getText()?.text?.trim().orEmpty()
                    if (clip.isNotEmpty()) inputText = clip
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("لصق", color = Color(0xFFCBD5E1))
            }
            Button(
                onClick = { performSearch() },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.weight(2f),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("بحث ومشاهدة", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = statusMessage.orEmpty(),
                color = Color(0xFFFCA5A5),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (recentHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "آخر المشاهدات",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 8.dp),
            ) {
                items(recentHistory, key = { it.contentKey }) { entry ->
                    RecentWatchCard(
                        entry = entry,
                        onClick = {
                            val type = if (entry.mediaType == "tv") InstantMediaType.TV else InstantMediaType.MOVIE
                            val item = InstantMediaItem(
                                tmdbId = entry.tmdbId,
                                mediaType = type,
                                title = entry.title,
                                originalTitle = entry.originalTitle ?: entry.title,
                                releaseYear = entry.releaseYear,
                                posterUrl = entry.posterUrl,
                                imdbId = entry.imdbId,
                            )
                            if (type == InstantMediaType.TV && (entry.seasonNumber == null || entry.episodeNumber == null)) {
                                handleMediaSelected(item)
                            } else {
                                startPlayback(item, entry.seasonNumber, entry.episodeNumber)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentWatchCard(
    entry: InstantWatchHistoryEntity,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center,
            ) {
                if (!entry.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.posterUrl,
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.seasonNumber != null && entry.episodeNumber != null) {
                    "متابعة • م${entry.seasonNumber} ح${entry.episodeNumber}"
                } else {
                    "متابعة المشاهدة"
                },
                color = Color(0xFF60A5FA),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GateMessageView(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = body,
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPrimary,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                ) {
                    Text(primaryLabel, color = Color.White)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    TextButton(onClick = onSecondary) {
                        Text(secondaryLabel, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}
