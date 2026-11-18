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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.SahabIcon
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.browse.ALL_CAT_ID
import com.saydlawy.sahab.ui.browse.FAV_CAT_ID
import com.saydlawy.sahab.ui.browse.RECENTS_CAT_ID
import com.saydlawy.sahab.ui.components.Chip
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.util.Ar

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
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasRecents) {
                item(key = RECENTS_CAT_ID) {
                    Chip(
                        Ar.continueWatching,
                        selectedId == RECENTS_CAT_ID,
                        { onSelect(RECENTS_CAT_ID) },
                        Modifier.restorableFocus(screenKey, "prime_cat_$RECENTS_CAT_ID"),
                    )
                }
            }
            item(key = FAV_CAT_ID) {
                Chip(
                    Ar.favorites,
                    selectedId == FAV_CAT_ID,
                    { onSelect(FAV_CAT_ID) },
                    Modifier.restorableFocus(screenKey, "prime_cat_$FAV_CAT_ID"),
                )
            }
            item(key = ALL_CAT_ID) {
                Chip(
                    Ar.all,
                    selectedId == ALL_CAT_ID || selectedId == null,
                    { onSelect(ALL_CAT_ID) },
                    Modifier.restorableFocus(screenKey, "prime_cat_$ALL_CAT_ID"),
                )
            }
            items(categories, key = { it.id }) { cat ->
                Chip(
                    cat.name,
                    cat.id == selectedId,
                    { onSelect(cat.id) },
                    Modifier.restorableFocus(screenKey, "prime_cat_${cat.id}"),
                )
            }
        }
    }

    if (isPortrait) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
        val searchBoxFocus = remember { FocusRequester() }
        LaunchedEffect(searchExpanded) {
            if (searchExpanded) runCatching { searchBoxFocus.requestFocus() }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (searchExpanded || query.isNotBlank()) {
                SearchBox(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = Ar.searchTitles,
                    modifier = Modifier
                        .width(240.dp)
                        .focusRequester(searchBoxFocus)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyUp && ev.key == Key.Back && query.isBlank()) {
                                searchExpanded = false
                                true
                            } else {
                                false
                            }
                        },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (searchBtnFocused) PrimeCyan else Palette.GlassStrong)
                        .border(if (searchBtnFocused) 2.5.dp else 1.dp, if (searchBtnFocused) Color.White else Palette.GlassBorder, CircleShape)
                        .onFocusChanged { searchBtnFocused = it.isFocused }
                        .clickable { searchExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    SahabIcon(
                        imageVector = Ic.Search,
                        contentDescription = Ar.searchTitles,
                        modifier = Modifier.size(20.dp),
                        tint = if (searchBtnFocused) Color.Black else Color.White,
                    )
                }
            }
            chipsRow(Modifier.weight(1f))
        }
    }
}
