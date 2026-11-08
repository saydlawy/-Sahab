package com.saydlawy.sahab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.saydlawy.sahab.R

object Palette {
    val Bg = Color(0xFF0B0A1F)
    val BgTop = Color(0xFF1B1446)
    val Violet = Color(0xFF8B6CFF)
    val Pink = Color(0xFFFF72C0)
    val Cyan = Color(0xFF5CE1FF)
    val Peach = Color(0xFFFFB690)
    val Text = Color(0xFFF7F5FF)
    val TextDim = Color(0xFFC3BEE3)
    val DialogBg = Color(0xF2171335)
    val Danger = Color(0xFFFF8FA3)
    val Accent: Brush = Brush.linearGradient(listOf(Violet, Pink))
}

val TajawalFamily = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

@Composable
fun SahabTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Palette.Violet,
        secondary = Palette.Pink,
        background = Palette.Bg,
        surface = Palette.Bg,
        onSurface = Palette.Text,
        onBackground = Palette.Text,
    )
    val base = Typography()
    val typography = base.copy(
        titleLarge = base.titleLarge.copy(fontFamily = TajawalFamily),
        titleMedium = base.titleMedium.copy(fontFamily = TajawalFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = TajawalFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = TajawalFamily),
        labelLarge = base.labelLarge.copy(fontFamily = TajawalFamily),
    )
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalTextStyle provides TextStyle(fontFamily = TajawalFamily, color = Palette.Text, fontSize = 16.sp),
            LocalContentColor provides Palette.Text,
        ) {
            content()
        }
    }
}
