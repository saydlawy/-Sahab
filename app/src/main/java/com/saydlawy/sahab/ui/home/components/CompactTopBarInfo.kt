package com.saydlawy.sahab.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.weather.WeatherCondition
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.theme.Palette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CompactTopBarInfo(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val weather by container.weather.weather.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        container.weather.refreshIfNeeded()
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    val timeStr = remember(now) {
        SimpleDateFormat("hh:mm a", Locale("ar")).format(Date(now))
    }

    val isSunny = weather.condition == WeatherCondition.CLEAR_DAY || weather.condition == WeatherCondition.PARTLY_CLOUDY

    Row(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${weather.tempC}°",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Icon(
            imageVector = if (isSunny) Ic.Sun else Ic.Cloud,
            contentDescription = weather.conditionText,
            tint = if (isSunny) Color(0xFFFBBF24) else Palette.FocusCyan,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = timeStr,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
