package com.saydlawy.sahab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saydlawy.sahab.data.Program
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatClock
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException

/** The day's program guide for one channel. */
@Composable
fun EpgDialog(channelName: String, channelId: String, onDismiss: () -> Unit) {
    val repo = LocalContainer.current.repo
    var programs by remember { mutableStateOf<List<Program>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(channelId, attempt) {
        programs = null
        error = null
        try {
            programs = repo.dayEpg(channelId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.DialogBg)
                .border(1.dp, Palette.Violet.copy(alpha = 0.45f), RoundedCornerShape(28.dp)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Ar.guide, fontSize = 15.sp, color = Palette.TextDim)
                    Text(
                        channelName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassIconButton(Ic.Close, Ar.close, onDismiss, size = 52.dp, autoFocus = true)
            }

            val list = programs
            when {
                error != null -> MessageBox(error.orEmpty(), actionLabel = Ar.retry, onAction = { attempt++ })
                list == null -> LoadingBox()
                list.isEmpty() -> MessageBox(Ar.noGuide)
                else -> ProgramList(list)
            }
        }
    }
}

@Composable
private fun ProgramList(programs: List<Program>) {
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val currentIndex = programs.indexOfFirst { nowSec in it.start until it.end }
    val listState = rememberLazyListState()
    LaunchedEffect(programs) {
        if (currentIndex > 0) listState.scrollToItem(currentIndex)
    }
    var expanded by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(programs, key = { index, p -> "${p.start}_$index" }) { index, program ->
            val isNow = index == currentIndex
            val isOpen = expanded == index
            GlassCard(
                onClick = { expanded = if (isOpen) null else index },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                selected = isNow,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${formatClock(program.start)} - ${formatClock(program.end)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isNow) Palette.Pink else Palette.TextDim,
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = program.title.ifBlank { Ar.noName },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (isOpen) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isNow) {
                            Text(Ar.nowShowing, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Cyan)
                        }
                    }
                    if (isNow) {
                        Spacer(Modifier.height(8.dp))
                        val total = (program.end - program.start).coerceAtLeast(1)
                        ProgressLine(((nowSec - program.start).toFloat() / total))
                    }
                    if (program.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = program.description,
                            fontSize = 15.sp,
                            color = Palette.TextDim,
                            maxLines = if (isOpen) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
