package com.saydlawy.sahab.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.HomeChannelsLayout
import com.saydlawy.sahab.data.HomeChannelsSource
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.SportsDatabase
import com.saydlawy.sahab.data.sports.toMatch
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.home.components.TrendingMoviesSection
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.ChannelLogoResolver
import com.saydlawy.sahab.util.formatDayOffsetYmd
import com.saydlawy.sahab.util.formatUtcClockToLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal val FALLBACK_FEATURED_CHANNELS = listOf(
    Channel("bein_sports_1", "beIN SPORTS 1 HD"),
    Channel("mbc_1", "MBC 1 HD"),
    Channel("mbc_action", "MBC Action HD"),
    Channel("mbc_drama", "MBC Drama HD"),
    Channel("rotana_cinema", "روتانا سينما"),
    Channel("aljazeera_news", "الجزيرة الإخبارية"),
    Channel("ssc_1", "SSC 1 HD"),
    Channel("alkass_1", "الكأس 1 HD"),
)

internal suspend fun loadFeaturedChannels(
    repo: Repository,
    source: HomeChannelsSource,
    categoryId: String,
    customIds: Set<String>,
    favorites: List<FavItem>,
): List<Channel> = withContext(Dispatchers.IO) {
    runCatching {
        val all = repo.peekAllLiveChannels() ?: repo.liveChannels(null)
        if (all.isEmpty()) return@withContext FALLBACK_FEATURED_CHANNELS

        when (source) {
            HomeChannelsSource.CUSTOM -> {
                if (customIds.isNotEmpty()) {
                    val byId = all.associateBy { it.id }
                    val picked = customIds.mapNotNull { byId[it] }
                    if (picked.isNotEmpty()) return@withContext picked
                }
            }
            HomeChannelsSource.FAVORITES -> {
                val favLiveIds = favorites.filter { it.kind == Kind.LIVE }.map { it.id }.toSet()
                if (favLiveIds.isNotEmpty()) {
                    val picked = all.filter { it.id in favLiveIds }
                    if (picked.isNotEmpty()) return@withContext picked
                }
            }
            HomeChannelsSource.CATEGORY -> {
                if (categoryId.isNotBlank()) {
                    val catChannels = all.filter { it.categoryId == categoryId }.ifEmpty {
                        runCatching { repo.liveChannels(categoryId) }.getOrDefault(emptyList())
                    }
                    if (catChannels.isNotEmpty()) return@withContext catChannels.take(18)
                }
            }
            HomeChannelsSource.AUTO -> Unit
        }

        val targetKeywords = listOf(
            listOf("bein sports 1", "bein sport 1", "بي ان سبورت 1"),
            listOf("mbc 1", "ام بي سي 1"),
            listOf("mbc action", "ام بي سي اكشن"),
            listOf("mbc masr", "ام بي سي مصر"),
            listOf("mbc drama", "ام بي سي دراما"),
            listOf("روتانا سينما", "rotana cinema"),
            listOf("روتانا خليجية", "rotana khalijiah", "rotana khalijia"),
            listOf("الجزيرة", "al jazeera", "aljazeera"),
            listOf("العربية", "alarabiya", "الحدث"),
            listOf("ssc 1", "ssc", "alkass", "الكأس", "on time"),
            listOf("ناشونال", "national geographic", "nat geo"),
            listOf("دبي", "dubai", "abu dhabi", "أبوظبي"),
        )

        val matched = mutableListOf<Channel>()
        val seen = mutableSetOf<String>()

        for (keywords in targetKeywords) {
            val candidate = all.firstOrNull { ch ->
                val lower = ch.name.lowercase(Locale.ROOT)
                keywords.any { kw -> lower.contains(kw.lowercase(Locale.ROOT)) } && seen.add(ch.id)
            }
            if (candidate != null) {
                matched.add(candidate)
            }
        }

        for (ch in all) {
            if (matched.size >= 14) break
            if (seen.add(ch.id)) {
                matched.add(ch)
            }
        }
        matched
    }.getOrDefault(FALLBACK_FEATURED_CHANNELS)
}

