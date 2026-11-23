package com.saydlawy.sahab.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.saydlawy.sahab.ui.components.GlassTextField
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.theme.Palette

@Composable
fun AdminCatalogEditDialog(
    initialTitle: String,
    initialPosterUrl: String,
    initialGenre: String,
    availablePosters: List<String>,
    availableGenres: List<String>,
    isLocked: Boolean,
    saving: Boolean,
    onSave: (title: String, posterUrl: String, genre: String, lockItem: Boolean) -> Unit,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var posterUrl by remember(initialPosterUrl) { mutableStateOf(initialPosterUrl) }
    var genre by remember(initialGenre) { mutableStateOf(initialGenre) }

    val posterChoices = remember(initialPosterUrl, availablePosters) {
        buildList {
            if (initialPosterUrl.isNotBlank()) add(initialPosterUrl.trim())
            addAll(availablePosters.map { it.trim() }.filter { it.isNotEmpty() })
        }.distinct()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF141821),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Ic.Tune,
                            contentDescription = null,
                            tint = Palette.FocusCyan,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "تعديل بيانات الأدمن",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    if (isLocked) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Ic.Lock,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "مقفل",
                                color = Color(0xFFF59E0B),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // 1. Title
                Text(
                    text = "العنوان",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                GlassTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "اكتب العنوان الصحيح",
                    leading = Ic.Movie,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 2. Poster Selection + Direct URL
                Text(
                    text = "البوستر الرئيسي (اختر من TMDB أو ضع رابطاً مباشراً)",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                if (posterChoices.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        itemsIndexed(posterChoices, key = { idx, url -> "$idx:$url" }) { _, url ->
                            AdminPosterOptionCard(
                                url = url,
                                selected = posterUrl.trim() == url,
                                onClick = { posterUrl = url },
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AdminPosterOptionCard(
                        url = posterUrl.trim(),
                        selected = true,
                        onClick = {},
                    )
                    GlassTextField(
                        value = posterUrl,
                        onValueChange = { posterUrl = it },
                        placeholder = "https://... رابط مباشر للبوستر",
                        leading = Ic.Link,
                        imeAction = ImeAction.Next,
                        modifier = Modifier.weight(1f),
                    )
                }

                // 3. Genre Selection + Custom Input
                Text(
                    text = "التصنيف (اختر تصنيفاً أو أضف تصنيفاً جديداً)",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                GlassTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    placeholder = "مثال: أكشن، دراما، كوميدي",
                    leading = Ic.List,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (availableGenres.isNotEmpty()) {
                    val activeTokens = remember(genre) {
                        genre.split(',', '،', '/')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toSet()
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        itemsIndexed(availableGenres, key = { idx, g -> "$idx:$g" }) { _, itemGenre ->
                            val isSelected = activeTokens.contains(itemGenre)
                            AdminGenreChip(
                                label = itemGenre,
                                selected = isSelected,
                                onClick = {
                                    val currentList = genre.split(',', '،', '/')
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .toMutableList()
                                    if (currentList.contains(itemGenre)) {
                                        currentList.remove(itemGenre)
                                    } else {
                                        currentList.add(itemGenre)
                                    }
                                    genre = currentList.joinToString("، ")
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 4. Action Buttons: Save | Save & Lock | Unlock | Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            onSave(title, posterUrl, genre, false)
                            onDismiss()
                        },
                        enabled = !saving && title.trim().isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("حفظ", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = {
                            onSave(title, posterUrl, genre, true)
                            onDismiss()
                        },
                        enabled = !saving && title.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.weight(1.25f),
                    ) {
                        Icon(
                            imageVector = Ic.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("حفظ وقفل", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLocked) {
                        OutlinedButton(
                            onClick = {
                                onUnlock()
                                onDismiss()
                            },
                            enabled = !saving,
                        ) {
                            Text("فك القفل")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPosterOptionCard(
    url: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val wPx = remember(density) { with(density) { 54.dp.roundToPx() } }
    val hPx = remember(density) { with(density) { 80.dp.roundToPx() } }
    val req = remember(context, url, wPx, hPx) {
        ImageRequest.Builder(context)
            .data(url.ifBlank { null })
            .size(wPx, hPx)
            .precision(Precision.INEXACT)
            .build()
    }

    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = when {
                    focused -> 2.5.dp
                    selected -> 2.dp
                    else -> 1.dp
                },
                color = when {
                    focused -> Palette.FocusCyan
                    selected -> Color(0xFF22C55E)
                    else -> Color.White.copy(alpha = 0.16f)
                },
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        AsyncImage(
            model = req,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selected && url.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141821)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Ic.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun AdminGenreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    focused -> Palette.FocusCyan.copy(alpha = 0.28f)
                    selected -> Palette.FocusCyan.copy(alpha = 0.2f)
                    else -> Color.White.copy(alpha = 0.07f)
                }
            )
            .border(
                width = if (focused || selected) 1.5.dp else 1.dp,
                color = when {
                    focused -> Palette.FocusCyan
                    selected -> Palette.FocusCyan.copy(alpha = 0.8f)
                    else -> Color.White.copy(alpha = 0.16f)
                },
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected || focused) Color.White else Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
