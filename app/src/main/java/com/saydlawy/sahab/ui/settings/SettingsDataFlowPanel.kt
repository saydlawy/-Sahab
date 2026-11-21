package com.saydlawy.sahab.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.DiskCache
import com.saydlawy.sahab.data.catalog.MediaFlowStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsDataFlowPanel(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val isAdmin = container.authManager.isAdmin
    val bgStatus by container.catalogSync.bgEnrichStatus.collectAsStateWithLifecycle()
    val fbMsg by container.catalogSync.firebaseSyncMessage.collectAsStateWithLifecycle()

    var movieStats by remember { mutableStateOf(MediaFlowStats()) }
    var seriesStats by remember { mutableStateOf(MediaFlowStats()) }
    var cacheStats by remember { mutableStateOf(DiskCache.CacheStats()) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun refreshStats() = withContext(Dispatchers.IO) {
        val dao = container.catalogDb.movieDao()
        movieStats = dao.getMediaFlowStats("movie")
        seriesStats = dao.getMediaFlowStats("series")
        cacheStats = container.diskCache.getCacheStats()
    }

    LaunchedEffect(Unit) { refreshStats() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("مسارات البيانات والإحصائيات", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

        FlowCard(title = "صلاحية التعديل السحابي (global_catalog)") {
            val roleText = if (isAdmin) {
                "أدمن وحيد — مسموح بالقراءة والكتابة والتعديل على السحابة"
            } else {
                "مستخدم عادي / ضيف — القراءة فقط من السحابة (الكتابة مغلقة)"
            }
            Text(roleText, fontSize = 13.sp, color = if (isAdmin) Color(0xFF4ADE80) else Color(0xFFFBBF24))
        }

        FlowCard(title = "الأفلام في Room") {
            MediaStatsRows(movieStats)
        }

        FlowCard(title = "المسلسلات في Room") {
            MediaStatsRows(seriesStats)
        }

        FlowCard(title = "الكاش المحلي (DiskCache)") {
            val catMb = "%.2f MB".format(cacheStats.catalogBytes / (1024.0 * 1024.0))
            val tmdbMb = "%.2f MB".format(cacheStats.tmdbBytes / (1024.0 * 1024.0))
            Text("كاش القوائم والـ EPG: ${cacheStats.catalogFiles} ملف ($catMb)", fontSize = 13.sp, color = Color(0xFFD1D5DB))
            Text("كاش TMDB الدائم: ${cacheStats.tmdbFiles} ملف ($tmdbMb)", fontSize = 13.sp, color = Color(0xFFD1D5DB))
        }

        if (bgStatus.active || fbMsg != null || actionMsg != null) {
            FlowCard(title = "الحالة الحالية") {
                fbMsg?.let { Text(it, fontSize = 13.sp, color = Color(0xFF60A5FA)) }
                if (bgStatus.active) {
                    Text(
                        "المعالجة: ${bgStatus.done}/${bgStatus.total} — ${bgStatus.currentTitle}",
                        fontSize = 13.sp,
                        color = Color(0xFF93C5FD),
                    )
                }
                if (bgStatus.failed > 0 && bgStatus.lastError != null) {
                    Text("آخر خطأ (${bgStatus.failed}): ${bgStatus.lastError}", fontSize = 12.sp, color = Color(0xFFF87171))
                }
                actionMsg?.let { Text(it, fontSize = 13.sp, color = Color(0xFF34D399)) }
            }
        }

        FlowCard(title = "التحكم في المسارات") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                var refreshFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            refreshStats()
                            actionMsg = "تم تحديث الإحصائيات"
                            busy = false
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .onFocusChanged { refreshFocused = it.isFocused }
                        .border(if (refreshFocused) 2.dp else 0.dp, if (refreshFocused) Color(0xFF38BDF8) else Color.Transparent, RoundedCornerShape(24.dp)),
                ) { Text("تحديث الأرقام") }

                var pullFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    enabled = !busy && !bgStatus.active,
                    onClick = {
                        scope.launch {
                            busy = true
                            val count = container.catalogSync.syncAllFromFirebaseToRoom(force = true)
                            refreshStats()
                            actionMsg = "تم سحب $count عنصر من السحابة إلى Room"
                            busy = false
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .onFocusChanged { pullFocused = it.isFocused }
                        .border(if (pullFocused) 2.dp else 0.dp, if (pullFocused) Color(0xFF38BDF8) else Color.Transparent, RoundedCornerShape(24.dp)),
                ) { Text("سحب: السحابة ← Room") }
            }

            Spacer(Modifier.height(8.dp))

            if (isAdmin) {
                var adminPushFocused by remember { mutableStateOf(false) }
                Button(
                    enabled = !busy && !bgStatus.active,
                    onClick = {
                        scope.launch {
                            busy = true
                            val pushed = container.catalogSync.pushLocalEnrichedToCloud()
                            refreshStats()
                            actionMsg = "تم رفع $pushed عنصر مكتمل إلى السحابة"
                            busy = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .onFocusChanged { adminPushFocused = it.isFocused }
                        .border(if (adminPushFocused) 2.dp else 0.dp, if (adminPushFocused) Color(0xFF38BDF8) else Color.Transparent, RoundedCornerShape(24.dp)),
                ) { Text("رفع (أدمن): Room ← السحابة") }
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                var retryFailedFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    enabled = !busy && (movieStats.failed + seriesStats.failed) > 0,
                    onClick = {
                        scope.launch {
                            busy = true
                            val reset = withContext(Dispatchers.IO) {
                                container.catalogDb.movieDao().resetFailedEnrichment()
                            }
                            refreshStats()
                            actionMsg = "تمت إعادة $reset عنصر فاشل لقائمة الانتظار"
                            busy = false
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .onFocusChanged { retryFailedFocused = it.isFocused }
                        .border(if (retryFailedFocused) 2.dp else 0.dp, if (retryFailedFocused) Color(0xFF38BDF8) else Color.Transparent, RoundedCornerShape(24.dp)),
                ) { Text("إعادة محاولة الفاشل") }

                var clearCacheFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            withContext(Dispatchers.IO) { container.diskCache.clearCatalog() }
                            refreshStats()
                            actionMsg = "تم مسح كاش القوائم بنجاح"
                            busy = false
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .onFocusChanged { clearCacheFocused = it.isFocused }
                        .border(if (clearCacheFocused) 2.dp else 0.dp, if (clearCacheFocused) Color(0xFF38BDF8) else Color.Transparent, RoundedCornerShape(24.dp)),
                ) { Text("مسح كاش القوائم") }
            }
        }
    }
}

@Composable
private fun MediaStatsRows(stats: MediaFlowStats) {
    val pct = if (stats.total > 0) (stats.enriched * 100) / stats.total else 0
    Text("الإجمالي: ${stats.total}  |  المكتمل: ${stats.enriched} ($pct%)", fontSize = 13.sp, color = Color.White)
    Text("منتظر للإثراء: ${stats.pending}  |  فاشل: ${stats.failed}", fontSize = 13.sp, color = Color(0xFFD1D5DB))
    Text("مرتبط بـ TMDB (جاهز للسحابة): ${stats.withTmdb}", fontSize = 13.sp, color = Color(0xFF93C5FD))
}

@Composable
private fun FlowCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141821), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE5E7EB))
        content()
    }
}
