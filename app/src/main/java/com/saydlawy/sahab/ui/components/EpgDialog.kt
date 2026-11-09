package com.saydlawy.sahab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
                .border(1.5.dp, Palette.Glow.copy(alpha = 0.45f), RoundedCornerShape(28.dp)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    DotTitle(Ar.epgTitle, Palette.Gold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        channelName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassIconButton(Ic.Close, Ar.close, onDismiss, size = 48.dp, autoFocus = true)
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
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(programs, key = { index, p -> "${p.start}_$index" }) { index, program ->
            val isNow = index == currentIndex
            val isPast = program.end <= nowSec
            val isOpen = expanded == index
            GlassCard(
                onClick = { expanded = if (isOpen) null else index },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                selected = isNow,
                focusScale = 1.015f,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${formatClock(program.start)} - ${formatClock(program.end)}",
                            fontSize = 14.sp,
                            fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                            color = if (isNow) Palette.Gold else Palette.TextDim,
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = program.title.ifBlank { Ar.noName },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPast && !isNow) Palette.TextDim else Palette.Text,
                            maxLines = if (isOpen) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        when {
                            isNow -> Tag(Ar.onAirNow, Palette.Live, filled = true)
                            isPast -> Tag(Ar.ended, Palette.TextDim)
                            else -> Tag(Ar.soon, Palette.Glow)
                        }
                    }
                    if (isNow) {
                        Spacer(Modifier.height(8.dp))
                        val total = (program.end - program.start).coerceAtLeast(1)
                        ProgressLine(((nowSec - program.start).toFloat() / total), height = 4.dp)
                    }
                    if (program.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = program.description,
                            fontSize = 14.sp,
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
