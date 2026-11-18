package com.saydlawy.sahab.ui.browse.layouts.prime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.data.Category
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SearchBox(
            value = query,
            onValueChange = onQuery,
            placeholder = Ar.searchTitles,
            modifier = Modifier.fillMaxWidth(0.28f),
        )
        LazyRow(
            state = rowState,
            modifier = Modifier.weight(1f),
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
}
