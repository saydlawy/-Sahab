package com.saydlawy.sahab.ui.live.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.channel.ChannelListEntity
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.theme.Palette

@Composable
internal fun ChannelManageContextSheet(
    channel: Channel,
    isFavorite: Boolean,
    customLists: List<ChannelListEntity>,
    onToggleFavorite: () -> Unit,
    onToggleInList: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHideChannel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            strong = true,
            padding = 20.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = channel.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                GlassButton(
                    text = if (isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                    primary = true,
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "تحريك للأعلى",
                        onClick = {
                            onMoveUp()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = "تحريك للأسفل",
                        onClick = {
                            onMoveDown()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (customLists.isNotEmpty()) {
                    DotTitle("إضافة / إزالة من قائمة مخصصة", Palette.FocusCyan, fontSize = 13.sp)
                    customLists.forEach { list ->
                        GlassButton(
                            text = list.name,
                            onClick = {
                                onToggleInList(list.listId)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                GlassButton(
                    text = "إخفاء القناة",
                    onClick = {
                        onHideChannel()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                GlassButton(
                    text = "إلغاء",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
