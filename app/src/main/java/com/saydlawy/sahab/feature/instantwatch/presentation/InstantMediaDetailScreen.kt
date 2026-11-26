package com.saydlawy.sahab.feature.instantwatch.presentation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.saydlawy.sahab.feature.instantwatch.data.*
import java.util.Locale

@Composable
internal fun InstantMediaDetailScreen(
    initialItem: InstantMediaItem,
    tmdbRepository: TmdbRepository,
    onPlayMovie: (InstantMediaItem) -> Unit,
    onPlayEpisode: (InstantMediaItem, Int, Int) -> Unit,
    onSelectSimilar: (InstantMediaItem) -> Unit,
    onSelectPerson: (Int, String) -> Unit = { _, _ -> },
    onSelectGenre: (Int, String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val clipboard = LocalClipboardManager.current
    val config = LocalConfiguration.current
    val isWide = config.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        config.screenWidthDp >= 720
    val horizontalPad = if (isWide) 24.dp else 16.dp

    var details by remember(initialItem.contentKey) { mutableStateOf<InstantMediaDetails?>(null) }
    var isLoading by remember(initialItem.contentKey) { mutableStateOf(true) }
    var selectedSeason by remember(initialItem.contentKey) { mutableStateOf(1) }
    var episodes by remember(initialItem.contentKey) { mutableStateOf<List<InstantEpisodeSummary>>(emptyList()) }
    var isEpisodesLoading by remember(initialItem.contentKey) { mutableStateOf(false) }
    var copiedHint by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    var playFocused by remember { mutableStateOf(false) }
    // Start with D-pad focus on the primary action instead of the navigation rail.
    LaunchedEffect(initialItem.contentKey) {
        delay(350L)
        runCatching { playFocus.requestFocus() }
    }

    LaunchedEffect(initialItem.contentKey) {
        isLoading = true
        val loaded = tmdbRepository.getFullDetails(initialItem.tmdbId, initialItem.mediaType)
        details = loaded
        isLoading = false
        val firstSeason = loaded?.item?.seasons?.firstOrNull()?.seasonNumber ?: 1
        selectedSeason = firstSeason
    }

    val item = details?.item ?: initialItem
    val isTvSeries = item.mediaType == InstantMediaType.TV

    LaunchedEffect(item.tmdbId, isTvSeries, selectedSeason) {
        if (isTvSeries && selectedSeason > 0) {
            isEpisodesLoading = true
            episodes = tmdbRepository.getSeasonEpisodes(item.tmdbId, selectedSeason)
            isEpisodesLoading = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120)),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 1. Backdrop Header + Poster + Main Meta
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isWide) 240.dp else 220.dp)
                    .background(Color(0xFF0F172A)),
            ) {
                val bgUrl = details?.backdropUrl ?: item.posterUrl
                if (!bgUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = bgUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0x880B1120), Color(0xCC0B1120), Color(0xFF0B1120)),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPad, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var backFocused by remember { mutableStateOf(false) }
                    Surface(
                        color = if (backFocused) Color(0xFF2563EB) else Color(0xCC1E293B),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .border(
                                width = if (backFocused) 2.dp else 1.dp,
                                color = if (backFocused) Color(0xFF38BDF8) else Color(0xFF334155),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .onFocusChanged { backFocused = it.isFocused }
                            .clickable(onClick = onBack),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "← رجوع",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                    Surface(
                        color = if (isTvSeries) Color(0xDD1D4ED8) else Color(0xCCFBBF24),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = if (isTvSeries) "مسلسل" else "فيلم",
                            color = if (isTvSeries) Color.White else Color(0xFF0B1120),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPad, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(104.dp).height(150.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    ) {
                        if (!item.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.posterUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.originalTitle.isNotBlank() && !item.originalTitle.equals(item.title, true)) {
                            Text(
                                text = item.originalTitle,
                                color = Color(0xFF94A3B8),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            item.rating?.let { score ->
                                Text(
                                    text = "★ ${String.format(Locale.US, "%.1f", score)}/10",
                                    color = Color(0xFFFBBF24),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                            item.releaseYear?.let {
                                Text("$it", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                            }
                            details?.runtimeMinutes?.takeIf { it > 0 }?.let { mins ->
                                Text("$mins دقيقة", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // 2. Primary Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPad),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        if (isTvSeries) {
                            val firstEp = episodes.firstOrNull()?.episodeNumber ?: 1
                            onPlayEpisode(item, selectedSeason, firstEp)
                        } else {
                            onPlayMovie(item)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(playFocus)
                        .onFocusChanged { playFocused = it.isFocused }
                        .border(
                            width = if (playFocused) 3.dp else 0.dp,
                            color = if (playFocused) Color(0xFF38BDF8) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                ) {
                    Text(
                        text = if (isTvSeries) "▶ تشغيل م$selectedSeason ح1" else "▶ مشاهدة فورية",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = {
                        val copyVal = item.imdbId ?: item.tmdbId.toString()
                        clipboard.setText(AnnotatedString(copyVal))
                        copiedHint = true
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                ) {
                    Text(
                        text = if (copiedHint) "تم النسخ ✓" else "نسخ ID",
                        color = Color(0xFFE2E8F0),
                    )
                }
            }
        }

        // 3. Genres + Overview
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPad),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val genrePairs = remember(item.genreIds, details?.genres) {
                    val names = details?.genres.orEmpty()
                    item.genreIds.mapIndexed { idx, gid ->
                        gid to (names.getOrNull(idx) ?: TmdbCatalog.genreNamesAr[gid].orEmpty())
                    }.filter { it.second.isNotBlank() }
                }
                if (genrePairs.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(genrePairs, key = { it.first }) { (gid, gName) ->
                            var gFocused by remember { mutableStateOf(false) }
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(
                                    if (gFocused) 2.dp else 1.dp,
                                    if (gFocused) Color(0xFF38BDF8) else Color(0xFF334155),
                                ),
                                modifier = Modifier
                                    .onFocusChanged { gFocused = it.isFocused }
                                    .clickable { onSelectGenre(gid, gName) },
                            ) {
                                Text(
                                    text = gName,
                                    color = Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF38BDF8),
                        trackColor = Color(0xFF1E293B),
                    )
                }
                if (item.overview.isNotBlank()) {
                    Text(
                        text = item.overview,
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                    )
                }
            }
        }

        // 4. Cast Row
        val cast = details?.cast.orEmpty()
        if (cast.isNotEmpty()) {
            item {
                SectionHeader(title = "طاقم العمل", horizontalPad = horizontalPad, accentColor = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = horizontalPad),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(cast, key = { it.id }) { member ->
                        var cFocused by remember { mutableStateOf(false) }
                        Column(
                            modifier = Modifier
                                .width(88.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (cFocused) 2.dp else 0.dp,
                                    color = if (cFocused) Color(0xFF38BDF8) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .onFocusChanged { cFocused = it.isFocused }
                                .clickable { onSelectPerson(member.id, member.name) }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF334155), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!member.profileUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = member.profileUrl,
                                        contentDescription = member.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text(
                                        text = member.name.take(1),
                                        color = Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(
                                text = member.name,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (member.character.isNotBlank()) {
                                Text(
                                    text = member.character,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Seasons & Episodes (TV Series only)
        if (isTvSeries && item.seasons.isNotEmpty()) {
            item {
                SectionHeader(title = "المواسم والحلقات", horizontalPad = horizontalPad)
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = horizontalPad),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(item.seasons, key = { it.seasonNumber }) { s ->
                        val active = selectedSeason == s.seasonNumber
                        FilterChip(
                            selected = active,
                            onClick = { selectedSeason = s.seasonNumber },
                            label = {
                                Text(
                                    text = "الموسم ${s.seasonNumber}",
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2563EB),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFFCBD5E1),
                            ),
                        )
                    }
                }
            }
            if (isEpisodesLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(28.dp))
                    }
                }
            } else {
                items(episodes, key = { "s${selectedSeason}_e${it.episodeNumber}" }) { ep ->
                    var epFocused by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPad)
                            .border(
                                width = if (epFocused) 2.dp else 1.dp,
                                color = if (epFocused) Color(0xFF38BDF8) else Color(0xFF263449),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .onFocusChanged { epFocused = it.isFocused }
                            .clickable { onPlayEpisode(item, selectedSeason, ep.episodeNumber) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF162033)),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(116.dp)
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F172A)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!ep.stillUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ep.stillUrl,
                                        contentDescription = ep.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text("▶", color = Color(0xFF38BDF8), fontSize = 20.sp)
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "${ep.episodeNumber}. ${ep.name}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ep.runtimeMinutes?.takeIf { it > 0 }?.let {
                                        Text("$it دقيقة", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                                    }
                                    ep.rating?.let {
                                        Text(
                                            text = "★ ${String.format(Locale.US, "%.1f", it)}",
                                            color = Color(0xFFFBBF24),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                if (ep.overview.isNotBlank()) {
                                    Text(
                                        text = ep.overview,
                                        color = Color(0xFFCBD5E1),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Similar Titles
        val similar = details?.similar.orEmpty()
        if (similar.isNotEmpty()) {
            item {
                MediaCarouselSection(
                    title = "قد يعجبك أيضاً",
                    subtitle = "أعمال مشابهة بنفس الأجواء",
                    items = similar,
                    horizontalPad = horizontalPad,
                    cardWidth = 132.dp,
                    cardImageHeight = 184.dp,
                    onSelect = onSelectSimilar,
                    onFocusItem = {},
                )
            }
        }
    }
}
