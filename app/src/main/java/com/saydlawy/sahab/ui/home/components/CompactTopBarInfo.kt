package com.saydlawy.sahab.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.data.weather.WeatherCondition
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.rememberNow
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.formatNowMillis
import java.util.Locale

@Composable
fun CompactTopBarInfo(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val weather by container.weather.weather.collectAsStateWithLifecycle()
    val now = rememberNow()

    LaunchedEffect(Unit) {
        container.weather.refreshIfNeeded()
    }

    val timeStr = remember(now) {
        formatNowMillis(now, "hh:mm a", Locale.forLanguageTag("ar"))
    }

    val (weatherIcon, weatherTint) = when (weather.condition) {
        WeatherCondition.CLEAR_DAY -> Ic.Sun to Color(0xFFFBBF24)
        WeatherCondition.CLEAR_NIGHT -> Ic.Cloud to Palette.Glow
        WeatherCondition.PARTLY_CLOUDY -> Ic.Sun to Color(0xFFFCD34D)
        WeatherCondition.CLOUDY, WeatherCondition.FOG -> Ic.Cloud to Palette.TextDim
        WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM -> Ic.Cloud to Palette.FocusCyan
        WeatherCondition.SNOW -> Ic.Cloud to Color.White
    }

    Row(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (weather.city.isNotBlank()) {
            Text(
                text = weather.city,
                color = Palette.TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = "${weather.tempC}°",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Icon(
            imageVector = weatherIcon,
            contentDescription = weather.conditionText,
            tint = weatherTint,
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
