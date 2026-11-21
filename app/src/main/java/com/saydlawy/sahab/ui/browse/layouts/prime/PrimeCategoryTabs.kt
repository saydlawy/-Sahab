package com.saydlawy.sahab.ui.browse.layouts.prime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.browse.ALL_CAT_ID
import com.saydlawy.sahab.ui.browse.FAV_CAT_ID
import com.saydlawy.sahab.ui.browse.RECENTS_CAT_ID
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.SahabIcon
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

private val CategoryTints = listOf(
    Color(0xFF00A8E1),
    Color(0xFF8E45FF),
    Color(0xFF10B981),
    Color(0xFFE0A23B),
    Color(0xFFEC4899),
    Color(0xFF3B82F6),
)

private fun categoryIconFor(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        "fifa" in n || "cup" in n || "كأس" in n || "رياض" in n -> Ic.Football
        "حديث" in n || "جديد" in n || "2026" in n || "2025" in n -> Ic.Star
        "رمضان" in n || "ramadan" in n || "عرب" in n || "مصر" in n -> Ic.Moon
        "أجنب" in n || "اجنب" in n || "آسيو" in n || "ترك" in n || "هند" in n -> Ic.Globe
        "مسلسل" in n -> Ic.Series
        else -> Ic.Movie
    }
}

@Composable
private fun PrimeCategoryPill(
    label: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val bgBrush = when {
        selected -> Brush.horizontalGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.45f)))
        focused -> Brush.horizontalGradient(listOf(accent.copy(alpha = 0.48f), Color(0xFF1E293B)))
        else -> Brush.horizontalGradient(listOf(accent.copy(alpha = 0.20f), Color(0xFF0F172A).copy(alpha = 0.72f)))
    }
    val borderColor = when {
        focused -> Color.White
        selected -> accent
        else -> accent.copy(alpha = 0.42f)
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SahabIcon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = if (selected || focused) Color.White else accent,
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PrimeCategoryTabs(
    screenKey: String,
    categories: List<Category>,
    selectedId: String?,
    query: String,
    hasRecents: Boolean,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val rowState = rememberRestorableLazyListState("${screenKey}_prime_tabs")
    val isPortrait = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    val chipsRow: @Composable (Modifier) -> Unit = { rowMod ->
        LazyRow(
            state = rowState,
            modifier = rowMod.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (hasRecents) {
                item(key = RECENTS_CAT_ID) {
                    PrimeCategoryPill(
                        label = Ar.continueWatching,
                        icon = Ic.Play,
                        accent = PrimeCyan,
                        selected = selectedId == RECENTS_CAT_ID,
                        onClick = { onSelect(RECENTS_CAT_ID) },
                        modifier = Modifier.restorableFocus(screenKey, "prime_cat_$RECENTS_CAT_ID"),
                    )
                }
            }
            item(key = FAV_CAT_ID) {
                PrimeCategoryPill(
                    label = Ar.favorites,
                    icon = Ic.Heart,
                    accent = Color(0xFFF43F5E),
                    selected = selectedId == FAV_CAT_ID,
                    onClick = { onSelect(FAV_CAT_ID) },
                    modifier = Modifier.restorableFocus(screenKey, "prime_cat_$FAV_CAT_ID"),
                )
            }
            item(key = ALL_CAT_ID) {
                PrimeCategoryPill(
                    label = Ar.all,
                    icon = Ic.FolderGrid,
                    accent = Color(0xFF8E45FF),
                    selected = selectedId == ALL_CAT_ID || selectedId == null,
                    onClick = { onSelect(ALL_CAT_ID) },
                    modifier = Modifier.restorableFocus(screenKey, "prime_cat_$ALL_CAT_ID"),
                )
            }
            itemsIndexed(categories, key = { _, c -> c.id }) { idx, cat ->
                PrimeCategoryPill(
                    label = cat.name,
                    icon = categoryIconFor(cat.name),
                    accent = CategoryTints[idx % CategoryTints.size],
                    selected = cat.id == selectedId,
                    onClick = { onSelect(cat.id) },
                    modifier = Modifier.restorableFocus(screenKey, "prime_cat_${cat.id}"),
                )
            }
        }
    }

    if (isPortrait) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SearchBox(
                value = query,
                onValueChange = onQuery,
                placeholder = Ar.searchTitles,
                modifier = Modifier.fillMaxWidth(),
            )
            chipsRow(Modifier.fillMaxWidth())
        }
    } else {
        var searchExpanded by remember { mutableStateOf(query.isNotBlank()) }
        var searchBtnFocused by remember { mutableStateOf(false) }
        var restoreSearchBtnFocus by remember { mutableStateOf(false) }
        val searchBoxFocus = remember { FocusRequester() }
        val searchBtnFocus = remember { FocusRequester() }
        LaunchedEffect(searchExpanded, restoreSearchBtnFocus) {
            if (searchExpanded) {
                runCatching { searchBoxFocus.requestFocus() }
            } else if (restoreSearchBtnFocus) {
                restoreSearchBtnFocus = false
                runCatching { searchBtnFocus.requestFocus() }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (searchExpanded || query.isNotBlank()) {
                SearchBox(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = Ar.searchTitles,
                    modifier = Modifier
                        .width(220.dp)
                        .focusRequester(searchBoxFocus)
                        .onPreviewKeyEvent { ev ->
                            if (ev.key == Key.Back && query.isBlank()) {
                                if (ev.type == KeyEventType.KeyUp && ev.nativeKeyEvent.repeatCount == 0) {
                                    restoreSearchBtnFocus = true
                                    searchExpanded = false
                                }
                                true
                            } else {
                                false
                            }
                        },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .focusRequester(searchBtnFocus)
                        .clip(CircleShape)
                        .background(if (searchBtnFocused) PrimeCyan else Palette.GlassStrong)
                        .border(if (searchBtnFocused) 2.dp else 1.dp, if (searchBtnFocused) Color.White else Palette.GlassBorder, CircleShape)
                        .onFocusChanged { searchBtnFocused = it.isFocused }
                        .clickable { searchExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    SahabIcon(
                        imageVector = Ic.Search,
                        contentDescription = Ar.searchTitles,
                        modifier = Modifier.size(16.dp),
                        tint = if (searchBtnFocused) Color.Black else Color.White,
                    )
                }
            }
            chipsRow(Modifier.weight(1f))
        }
    }
}
