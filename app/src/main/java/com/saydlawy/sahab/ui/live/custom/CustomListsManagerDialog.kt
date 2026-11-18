package com.saydlawy.sahab.ui.live.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.saydlawy.sahab.data.channel.ChannelListEntity
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.GlassTextField
import com.saydlawy.sahab.ui.theme.Palette

@Composable
internal fun CustomListsManagerDialog(
    lists: List<ChannelListEntity>,
    onCreateList: (String) -> Unit,
    onEditChannels: (ChannelListEntity) -> Unit,
    onDeleteList: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp),
            strong = true,
            padding = 20.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "إدارة القوائم المخصصة",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Palette.Text,
                    )
                    GlassButton(text = "إغلاق", onClick = onDismiss)
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = "اسم القائمة الجديدة (مثل: الرياضة، الأخبار)",
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = "إنشاء",
                        primary = true,
                        onClick = {
                            val trimmed = newName.trim()
                            if (trimmed.isNotEmpty()) {
                                onCreateList(trimmed)
                                newName = ""
                            }
                        },
                    )
                }

                if (lists.isEmpty()) {
                    Text(
                        text = "لا توجد قوائم مخصصة بعد. أنشئ قائمة جديدة بالأعلى.",
                        color = Palette.TextDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(lists, key = { it.listId }) { list ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = list.name,
                                    color = Palette.Text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                GlassButton(
                                    text = "القنوات",
                                    onClick = { onEditChannels(list) },
                                )
                                GlassButton(
                                    text = "حذف",
                                    onClick = { onDeleteList(list.listId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
