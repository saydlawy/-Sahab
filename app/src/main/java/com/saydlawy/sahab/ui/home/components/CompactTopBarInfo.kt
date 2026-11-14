package com.saydlawy.sahab.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.theme.Palette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CompactTopBarInfo() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }
    val timeString = timeFormat.format(Date(now))
    val tempString = "24°م" // يمكنك ربطها بـ WeatherService لاحقاً

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Ic.Sun, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(16.dp))
        Text(tempString, fontSize = 13.sp, color = Palette.TextDim)
        
        Box(Modifier.width(1.dp).height(12.dp).background(Palette.TextDim.copy(alpha = 0.5f)))
        
        Text(timeString, fontSize = 13.sp, color = Palette.Text, fontWeight = FontWeight.Bold)
    }
}
