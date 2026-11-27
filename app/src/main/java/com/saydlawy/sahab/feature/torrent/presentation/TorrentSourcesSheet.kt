package com.saydlawy.sahab.feature.torrent.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.feature.torrent.domain.TorrentStreamCandidate
import java.util.Locale

private enum class QualityFilter(val labelAr: String) {
    ALL("الكل"),
    INSTANT("فوري ⚡"),
    UHD_4K("4K"),
    FHD_1080("1080p"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TorrentSourcesSheet(
    title: String,
    streams: List<TorrentStreamCandidate>,
    isLoading: Boolean,
    resolvingId: String?,
    errorMessage: String?,
    onSelect: (TorrentStreamCandidate) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    var selectedFilter by remember { mutableStateOf(QualityFilter.ALL) }
    val filtered = remember(streams, selectedFilter) {
        when (selectedFilter) {
            QualityFilter.ALL -> streams
            QualityFilter.INSTANT -> streams.filter { it.isInstantCached }
            QualityFilter.UHD_4K -> streams.filter { it.resolutionRank >= 4 }
            QualityFilter.FHD_1080 -> streams.filter { it.resolutionRank == 3 }
        }
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(isLoading, filtered.size) {
        if (!isLoading) {
            runCatching { firstFocus.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC020617))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF334155)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مصادر 4K / Blu-ray",
                            color = Color(0xFF38BDF8),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = title,
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FocusableChip(
                        label = "إغلاق ✕",
                        selected = false,
                        onClick = onDismiss,
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QualityFilter.entries) { filter ->
                        val count = when (filter) {
                            QualityFilter.ALL -> streams.size
                            QualityFilter.INSTANT -> streams.count { it.isInstantCached }
                            QualityFilter.UHD_4K -> streams.count { it.resolutionRank >= 4 }
                            QualityFilter.FHD_1080 -> streams.count { it.resolutionRank == 3 }
                        }
                        FocusableChip(
                            label = "${filter.labelAr} ($count)",
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33EF4444))
                            .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFCA5A5),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        FocusableChip(
                            label = "إعادة المحاولة",
                            selected = false,
                            onClick = onRetry,
                        )
                    }
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(color = Color(0xFF38BDF8))
                                Text(
                                    text = "جارٍ البحث في الإضافات عن أفضل جودات 4K و Blu-ray...",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }

                    filtered.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (streams.isEmpty()) {
                                    "لم يتم العثور على مصادر لهذا العمل. تأكد من تفعيل إضافة Stremio في الإعدادات."
                                } else {
                                    "لا توجد مصادر مطابقة لهذا الفلتر."
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {
                            items(filtered, key = { it.id }) { item ->
                                val isFirst = filtered.firstOrNull()?.id == item.id
                                TorrentCandidateCard(
                                    candidate = item,
                                    isResolving = resolvingId == item.id,
                                    enabled = resolvingId == null,
                                    onClick = { onSelect(item) },
                                    modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TorrentCandidateCard(
    candidate: TorrentStreamCandidate,
    isResolving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        focused -> Color(0xFF38BDF8)
        candidate.isInstantCached -> Color(0xFF10B981)
        else -> Color(0xFF334155)
    }
    val bgColor = if (focused) Color(0xFF1E293B) else Color(0xFF111827)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(if (focused) 2.dp else 1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (candidate.isInstantCached) {
                        MetaBadge("⚡ فوري", Color(0xFF065F46), Color(0xFF6EE7B7))
                    }
                    if (candidate.resolution.isNotBlank()) {
                        val is4k = candidate.resolutionRank >= 4
                        MetaBadge(
                            text = candidate.resolution,
                            bg = if (is4k) Color(0xFF7C3AED) else Color(0xFF1D4ED8),
                            fg = Color.White,
                        )
                    }
                    if (candidate.sourceTag.isNotBlank()) {
                        MetaBadge(candidate.sourceTag, Color(0xFF0F766E), Color(0xFFCCFBF1))
                    }
                    if (candidate.hdrTag.isNotBlank()) {
                        MetaBadge(candidate.hdrTag, Color(0xFFB45309), Color(0xFFFEF3C7))
                    }
                    if (candidate.audioTag.isNotBlank()) {
                        MetaBadge("🔊 ${candidate.audioTag}", Color(0xFF334155), Color(0xFFE2E8F0))
                    }
                    candidate.sizeGb?.let { gb ->
                        MetaBadge(
                            text = String.format(Locale.US, "%.1f GB", gb),
                            bg = Color(0xFF1E293B),
                            fg = Color(0xFF93C5FD),
                        )
                    }
                    candidate.seeders?.let { s ->
                        MetaBadge("👤 $s", Color(0xFF1E293B), Color(0xFF86EFAC))
                    }
                }

                Text(
                    text = candidate.rawTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = candidate.addonName,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                )
            }

            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = Color(0xFF38BDF8),
                )
            } else {
                Text(
                    text = "▶",
                    color = if (focused) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                    fontSize = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun MetaBadge(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FocusableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> Color(0xFF0284C7)
        focused -> Color(0xFF334155)
        else -> Color(0xFF1E293B)
    }
    val border = if (focused) Color(0xFF38BDF8) else Color(0xFF475569)
    Surface(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(if (focused) 2.dp else 1.dp, border),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}
