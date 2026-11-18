package com.saydlawy.sahab.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.HomeChannelsSource
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsCustomTvPanel(
    container: AppContainer,
    homeChannelsSource: HomeChannelsSource,
    homeChannelsCategoryId: String,
    homeCustomChannelIds: Set<String>,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dao = container.channelDb.channelDao()
    val activeAccountId by container.prefs.activeAccountId.collectAsStateWithLifecycle(
        initialValue = container.prefs.currentActiveAccountId(),
    )
    val customLists by remember(dao, activeAccountId) {
        dao.observeLists(activeAccountId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val hiddenIds by dao.observeHiddenIds().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HomeChannelsSelectionPanel(
            container = container,
            source = homeChannelsSource,
            selectedCategoryId = homeChannelsCategoryId,
            customChannelIds = homeCustomChannelIds,
            onSelectSource = { src -> scope.launch { container.prefs.setHomeChannelsSource(src) } },
            onSelectCategory = { catId ->
                scope.launch {
                    container.prefs.setHomeChannelsCategoryId(catId)
                    container.prefs.setHomeChannelsSource(HomeChannelsSource.CATEGORY)
                }
            },
            onToggleCustomChannel = { chId ->
                scope.launch {
                    container.prefs.toggleHomeCustomChannel(chId)
                    container.prefs.setHomeChannelsSource(HomeChannelsSource.CUSTOM)
                }
            },
            onClearCustomChannels = {
                scope.launch { container.prefs.setHomeCustomChannelIds(emptySet()) }
            },
        )

        GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DotTitle("قوائم البث المخصصة والقنوات المخفية", Palette.FocusCyan, fontSize = 18.sp)
                Text(
                    text = "يمكنك إنشاء وتعديل القوائم وترتيب القنوات وإخفائها مباشرة من شاشة البث المباشر.",
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                )

                InfoRow("عدد القوائم المخصصة", "${customLists.size}")
                InfoRow("عدد القنوات المخفية", "${hiddenIds.size}")

                if (customLists.isNotEmpty()) {
                    customLists.forEach { list ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(list.name, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            GlassButton(
                                text = "حذف القائمة",
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        dao.clearListItems(list.listId)
                                        dao.deleteList(list.listId)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "تم حذف قائمة ${list.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                if (hiddenIds.isNotEmpty()) {
                    GlassButton(
                        text = "إظهار جميع القنوات المخفية (${hiddenIds.size})",
                        primary = true,
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                dao.unhideAll(System.currentTimeMillis())
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "تمت استعادة جميع القنوات المخفية", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
