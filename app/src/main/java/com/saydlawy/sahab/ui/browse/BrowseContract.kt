package com.saydlawy.sahab.ui.browse

import androidx.compose.runtime.Immutable
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.RecentItem

@Immutable
data class CategoryPosterGroup(
    val category: Category,
    val posters: List<Poster>,
)

@Immutable
data class FocusedPosterDetails(
    val poster: Poster,
    val plot: String = "",
    val genre: String = "",
    val year: String = "",
    val rating: String = "",
    val runtime: String = "",
    val backdropUrl: String = "",
)

@Immutable
data class BrowseLayoutCallbacks(
    val onSelectCategory: (String) -> Unit,
    val onQuery: (String) -> Unit,
    val onOpenPoster: (Poster) -> Unit,
    val onOpenPosterWithKey: (Poster, String) -> Unit = { poster, _ -> onOpenPoster(poster) },
    val onToggleFavorite: (Poster) -> Unit,
    val onPosterFocused: (Poster) -> Unit,
    val onPlayRecent: (RecentItem) -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
)
