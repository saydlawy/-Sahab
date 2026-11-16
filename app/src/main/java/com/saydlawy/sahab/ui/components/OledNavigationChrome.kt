package com.saydlawy.sahab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

private data class ChromeTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val chromeTabs = listOf(
    ChromeTab(Routes.HOME, Ar.home, Ic.Home),
    ChromeTab(Routes.LIVE, "البث", Ic.Tv),
    ChromeTab(Routes.MOVIES, "الأفلام", Ic.Movie),
    ChromeTab(Routes.SERIES, "المسلسلات", Ic.Series),
    ChromeTab(Routes.SPORTS, Ar.sports, Ic.Football),
    ChromeTab(Routes.FAVORITES, Ar.favorites, Ic.Heart),
)

@Composable
internal fun OledNavigationChrome(current: String?, onTab: (String) -> Unit, mode: DisplayMode) {
    if (mode == DisplayMode.PHONE_PORTRAIT) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp)).background(Palette.CardSurface)
                .border(1.dp, Palette.CardBorderIdle, RoundedCornerShape(20.dp)).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chromeTabs.forEach { tab -> ChromeTabItem(tab, current, onTab, vertical = true) }
        }
    } else {
        Column(
            modifier = Modifier.width(76.dp).fillMaxHeight().padding(horizontal = 8.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(18.dp)).background(Palette.CardSurface)
                .border(1.dp, Palette.CardBorderIdle, RoundedCornerShape(18.dp)).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            chromeTabs.forEach { tab -> ChromeTabItem(tab, current, onTab, vertical = false) }
        }
    }
}

@Composable
private fun ChromeTabItem(tab: ChromeTab, current: String?, onTab: (String) -> Unit, vertical: Boolean) {
    val selected = current == tab.route
    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Palette.CardSurfaceElevated else Color.Transparent)
            .clickable { onTab(tab.route) }.padding(horizontal = 7.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SahabIcon(tab.icon, tab.label, Modifier.size(if (vertical) 20.dp else 22.dp), if (selected) Palette.FocusCyan else Palette.TextMuted)
        if (vertical) Text(tab.label, color = if (selected) Palette.FocusCyan else Palette.TextMuted, fontSize = 9.sp, maxLines = 1)
    }
}
