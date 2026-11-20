package com.saydlawy.sahab.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import com.saydlawy.sahab.data.TmdbSearchResult
import com.saydlawy.sahab.ui.components.GlassTextField
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.RatingSanitizer
import kotlinx.coroutines.launch

@Composable
fun TmdbMatchPickerDialog(
    initialQuery: String,
    onSearch: suspend (String) -> List<TmdbSearchResult>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var loading by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<TmdbSearchResult>>(emptyList()) }

    fun runSearch(q: String) {
        scope.launch {
            loading = true
            results = runCatching { onSearch(q) }.getOrElse { emptyList() }
            loading = false
        }
    }

    LaunchedEffect(initialQuery) {
        runSearch(initialQuery)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF141821),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "تصحيح البيانات (TMDB)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "ابحث بالاسم الإنجليزي أو العربي",
                        leading = Ic.Search,
                        imeAction = ImeAction.Search,
                        onImeAction = { runSearch(query) },
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { runSearch(query) }, enabled = !loading) {
                        Text("بحث")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 380.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        results.isEmpty() -> Text(
                            text = "لا توجد نتائج — جرّب تعديل الاسم واضغط بحث",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        else -> LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(results, key = { it.id }) { item ->
                                TmdbCandidateRow(
                                    item = item,
                                    onClick = {
                                        onSelect(item.id)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }
}

@Composable
private fun TmdbCandidateRow(
    item: TmdbSearchResult,
    onClick: () -> Unit,
) {
    val year = item.releaseDate.take(4)
    val rating = RatingSanitizer.format(item.voteAverage, item.voteCount)
    val posterUrl = item.posterPath?.let { "https://image.tmdb.org/t/p/w185$it" }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Palette.FocusCyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Palette.FocusCyan else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val posterWPx = remember(density) { with(density) { 48.dp.roundToPx() } }
        val posterHPx = remember(density) { with(density) { 72.dp.roundToPx() } }
        val posterRequest = remember(context, posterUrl, posterWPx, posterHPx) {
            ImageRequest.Builder(context)
                .data(posterUrl)
                .size(posterWPx, posterHPx)
                .precision(Precision.INEXACT)
                .build()
        }
        AsyncImage(
            model = posterRequest,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 48.dp, height = 72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title.ifBlank { item.originalTitle },
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.originalTitle.isNotBlank() && item.originalTitle != item.title) {
                Text(
                    text = item.originalTitle,
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            val meta = listOfNotNull(
                year.takeIf { it.isNotBlank() },
                rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
            ).joinToString("  •  ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = Color(0xFF4ADE80),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
