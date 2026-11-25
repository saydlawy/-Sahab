package com.saydlawy.sahab.feature.instantwatch.player

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.charset.Charset

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

object SubtitleParser {
    private val timeRegex = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})"""
    )
    private val shortTimeRegex = Regex(
        """(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2})[,.](\d{3})"""
    )
    private val htmlTagRegex = Regex("<[^>]+>")

    fun loadFromUri(context: Context, uri: Uri): List<SubtitleCue> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
        val utf8Text = bytes.toString(Charsets.UTF_8)
        val raw = if (utf8Text.contains('\uFFFD')) {
            runCatching { bytes.toString(Charset.forName("windows-1256")) }.getOrDefault(utf8Text)
        } else {
            utf8Text
        }
        return parse(raw)
    }

    fun parse(content: String): List<SubtitleCue> {
        val normalized = content
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val blocks = normalized.split(Regex("\n\\s*\n"))
        val cues = ArrayList<SubtitleCue>(blocks.size)

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            var timeLineIdx = -1
            var startMs = -1L
            var endMs = -1L

            for (i in lines.indices) {
                val m = timeRegex.find(lines[i])
                if (m != null) {
                    startMs = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
                    endMs = toMs(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8])
                    timeLineIdx = i
                    break
                }
                val sm = shortTimeRegex.find(lines[i])
                if (sm != null) {
                    startMs = toMs("0", sm.groupValues[1], sm.groupValues[2], sm.groupValues[3])
                    endMs = toMs("0", sm.groupValues[4], sm.groupValues[5], sm.groupValues[6])
                    timeLineIdx = i
                    break
                }
            }

            if (timeLineIdx >= 0 && timeLineIdx + 1 < lines.size && endMs > startMs) {
                val text = lines.subList(timeLineIdx + 1, lines.size)
                    .joinToString("\n")
                    .replace(htmlTagRegex, "")
                    .trim()
                if (text.isNotEmpty()) {
                    cues.add(SubtitleCue(startMs, endMs, text))
                }
            }
        }
        return cues.sortedBy { it.startMs }
    }

    private fun toMs(h: String, m: String, s: String, ms: String): Long {
        return h.toLong() * 3_600_000L +
            m.toLong() * 60_000L +
            s.toLong() * 1_000L +
            ms.toLong()
    }
}

@Composable
fun SubtitleOverlay(
    cues: List<SubtitleCue>,
    currentPositionMs: Long,
    offsetMs: Long,
    fontSizeSp: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible || cues.isEmpty()) return

    val adjustedMs = (currentPositionMs + offsetMs).coerceAtLeast(0L)
    val activeText = remember(cues, adjustedMs) {
        cues.firstOrNull { adjustedMs in it.startMs..it.endMs }?.text
    }

    if (activeText.isNullOrBlank()) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 44.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = activeText,
            color = Color.White,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = (fontSizeSp + 6).sp,
            modifier = Modifier
                .background(Color(0xB3000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
