package com.saydlawy.sahab.ui.live.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.channel.ChannelListEntity
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.normalizeForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun CustomListChannelPickerDialog(
    list: ChannelListEntity,
    allChannels: List<Channel>,
    initialOrderedIds: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var orderedIds by remember(list.listId, initialOrderedIds) { mutableStateOf(initialOrderedIds) }
    var filteredChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }

    LaunchedEffect(allChannels, query, orderedIds) {
        if (query.isNotBlank()) delay(150L)
        val selectedSet = orderedIds.toHashSet()
        val result = withContext(Dispatchers.Default) {
            val needle = query.normalizeForSearch().trim()
            if (needle.isBlank()) {
                val byId = allChannels.associateBy { it.id }
                val selected = orderedIds.mapNotNull { byId[it] }
                val rest = allChannels.asSequence().filter { it.id !in selectedSet }.take(150).toList()
                selected + rest
            } else {
                allChannels.asSequence()
                    .filter { it.name.normalizeForSearch().contains(needle) }
                    .take(150)
                    .toList()
            }
        }
        filteredChannels = result
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp),
            shape = RoundedCornerShape(24.dp),
            strong = true,
            padding = 18.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "قنوات قائمة: ${list.name}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${orderedIds.size} قناة مختارة",
                            fontSize = 12.sp,
                            color = Palette.FocusCyan,
                        )
                    }
                    GlassButton(
                        text = "حفظ",
                        primary = true,
                        onClick = { onSave(orderedIds) },
                    )
                }

                SearchBox(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "ابحث عن قناة لإضافتها أو إزالتها...",
                    modifier = Modifier.fillMaxWidth(),
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(filteredChannels, key = { idx, ch -> "${ch.id}_$idx" }) { _, ch ->
                        val checked = ch.id in orderedIds
                        GlassCard(
                            onClick = {
                                orderedIds = if (checked) orderedIds - ch.id else orderedIds + ch.id
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            selected = checked,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Palette.FocusCyan,
                                        uncheckedColor = Palette.TextDim,
                                        checkmarkColor = Palette.CinematicBg,
                                    ),
                                )
                                Text(
                                    text = ch.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
