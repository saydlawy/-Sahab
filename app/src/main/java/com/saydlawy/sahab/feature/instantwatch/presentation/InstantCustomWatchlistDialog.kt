package com.saydlawy.sahab.feature.instantwatch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.feature.instantwatch.data.TmdbRepository
import com.saydlawy.sahab.feature.instantwatch.local.InstantCustomWatchlist
import com.saydlawy.sahab.feature.instantwatch.local.InstantWatchlistEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun CustomWatchlistsCarouselSection(
    watchlists: List<InstantCustomWatchlist>,
    horizontalPad: Dp,
    onCreateClick: () -> Unit,
    onOpenWatchlist: (InstantCustomWatchlist) -> Unit,
    onDeleteWatchlist: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "قوائم المشاهدة الخاصة بك",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "أنشئ قائمة من اسم ممثل أو قائمة أفلام محددة",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            var addFocused by remember { mutableStateOf(false) }
            Surface(
                color = if (addFocused) Color(0xFF2563EB) else Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .border(
                        width = if (addFocused) 2.dp else 1.dp,
                        color = if (addFocused) Color(0xFF38BDF8) else Color(0xFF334155),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .onFocusChanged { addFocused = it.isFocused }
                    .clickable(onClick = onCreateClick),
            ) {
                Text(
                    text = "+ قائمة جديدة",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (watchlists.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizontalPad),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(watchlists, key = { it.id }) { wl ->
                    var focused by remember { mutableStateOf(false) }
                    val isActor = wl.kind == InstantCustomWatchlist.KIND_ACTOR
                    Surface(
                        color = if (focused) Color(0xFF1D4ED8) else Color(0xFF162033),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(210.dp)
                            .border(
                                width = if (focused) 2.dp else 1.dp,
                                color = if (focused) Color(0xFF38BDF8) else Color(0xFF334155),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .onFocusChanged { focused = it.isFocused }
                            .clickable { onOpenWatchlist(wl) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (isActor) "👤 قائمة ممثل" else "🎬 قائمة مخصصة",
                                    color = Color(0xFF38BDF8),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "حذف",
                                    color = Color(0xFFF87171),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clickable { onDeleteWatchlist(wl.id) },
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = wl.name,
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (isActor) "اضغط لعرض كل الأعمال" else "${wl.items.size} عمل محفوظ",
                                color = Color(0xFF94A3B8),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CreateWatchlistDialog(
    tmdbRepository: TmdbRepository,
    onDismiss: () -> Unit,
    onCreated: (InstantCustomWatchlist) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(InstantCustomWatchlist.KIND_ACTOR) }
    var listName by remember { mutableStateOf("") }
    var rawInput by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE2E8F0),
        title = {
            Text(
                text = "إنشاء قائمة مشاهدة جديدة",
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == InstantCustomWatchlist.KIND_ACTOR,
                        onClick = { mode = InstantCustomWatchlist.KIND_ACTOR; errorMsg = null },
                        label = { Text("حسب اسم ممثل") },
                    )
                    FilterChip(
                        selected = mode == InstantCustomWatchlist.KIND_CUSTOM,
                        onClick = { mode = InstantCustomWatchlist.KIND_CUSTOM; errorMsg = null },
                        label = { Text("قائمة أفلام محددة") },
                    )
                }

                if (mode == InstantCustomWatchlist.KIND_ACTOR) {
                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = { rawInput = it },
                        label = { Text("اسم الممثل أو المخرج") },
                        placeholder = { Text("مثال: Tom Cruise أو عادل إمام") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = listName,
                        onValueChange = { listName = it },
                        label = { Text("اسم القائمة") },
                        placeholder = { Text("مثال: أفلام السهرة أو روائع الغموض") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = { rawInput = it },
                        label = { Text("أسماء الأفلام (كل اسم في سطر أو بفاصلة)") },
                        placeholder = { Text("Inception\nInterstellar\nThe Prestige") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = Color(0xFFF87171),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    scope.launch {
                        isBusy = true
                        errorMsg = null
                        if (mode == InstantCustomWatchlist.KIND_ACTOR) {
                            val q = rawInput.trim()
                            if (q.isBlank()) {
                                errorMsg = "يرجى كتابة اسم الممثل."
                                isBusy = false
                                return@launch
                            }
                            val people = tmdbRepository.searchPeople(q)
                            val first = people.firstOrNull()
                            if (first == null) {
                                errorMsg = "لم يتم العثور على ممثل بهذا الاسم."
                                isBusy = false
                                return@launch
                            }
                            onCreated(
                                InstantCustomWatchlist(
                                    id = "actor_${first.first}",
                                    name = first.second,
                                    kind = InstantCustomWatchlist.KIND_ACTOR,
                                    personId = first.first,
                                ),
                            )
                        } else {
                            val title = listName.trim().ifBlank { "قائمتي المخصصة" }
                            val queries = rawInput
                                .split("\n", ",", "،")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .take(25)
                            if (queries.isEmpty()) {
                                errorMsg = "يرجى كتابة اسم فيلم واحد على الأقل."
                                isBusy = false
                                return@launch
                            }
                            val resolved = coroutineScope {
                                queries.map { q ->
                                    async { tmdbRepository.searchMulti(q).firstOrNull() }
                                }.awaitAll().filterNotNull().distinctBy { it.contentKey }
                            }
                            if (resolved.isEmpty()) {
                                errorMsg = "لم يتم العثور على أعمال مطابقة للأسماء المدخلة."
                                isBusy = false
                                return@launch
                            }
                            val entries = resolved.map { m ->
                                InstantWatchlistEntry(
                                    tmdbId = m.tmdbId,
                                    mediaType = m.mediaType.apiValue,
                                    title = m.title,
                                    originalTitle = m.originalTitle,
                                    releaseYear = m.releaseYear,
                                    posterUrl = m.posterUrl,
                                    rating = m.rating,
                                )
                            }
                            onCreated(
                                InstantCustomWatchlist(
                                    id = "custom_${System.currentTimeMillis()}",
                                    name = title,
                                    kind = InstantCustomWatchlist.KIND_CUSTOM,
                                    items = entries,
                                ),
                            )
                        }
                        isBusy = false
                    }
                },
            ) {
                Text(if (isBusy) "جاري الإنشاء..." else "حفظ القائمة")
            }
        },
        dismissButton = {
            TextButton(enabled = !isBusy, onClick = onDismiss) {
                Text("إلغاء")
            }
        },
    )
}
