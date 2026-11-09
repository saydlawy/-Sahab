package com.saydlawy.sahab.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CyberBg = Color(0xFF0B0716)
val CardBg = Color(0xFF18112B)
val PrimaryAccent = Color(0xFF8B3DFF)
val CyanAccent = Color(0xFF00F5D4)
val LiveRed = Color(0xFFE63946)
val GoldAccent = Color(0xFFF59E0B)

@Composable
fun SportsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBg)
            .padding(16.dp)
    ) {
        TopHeaderBar()
        Spacer(modifier = Modifier.height(16.dp))
        FilterBar()
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("• الدوري المصري الممتاز - الجولة 27", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("استاد القاهرة الدولي • بث مباشر", color = Color.Gray, fontSize = 12.sp)
                }
                FeaturedMatchCard()
            }

            Column(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                MatchDetailPanel()
            }
        }
    }
}

@Composable
fun FeaturedMatchCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, PrimaryAccent, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("• مباشر LIVE '68", color = Color.White, modifier = Modifier.background(LiveRed, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("الشوط الثاني", color = Color.Gray, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OnTime Sports 1 HD", color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp)
                    Text("FHD 50FPS", color = CyanAccent, modifier = Modifier.background(Color.Blue.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("النادي الأهلي", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("المضيف", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(LiveRed.copy(alpha = 0.2f)).border(1.dp, LiveRed, CircleShape), contentAlignment = Alignment.Center) {
                        Text("الأهلي", color = LiveRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("2 : 1", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("مدحت شلبي", color = CyanAccent, fontSize = 12.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Text("الزمالك", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("نادي الزمالك", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("الضيف", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("وسام أبو علي '24، إمام عاشور '56", color = GoldAccent, fontSize = 12.sp)
                Text("زيزو '42 (ض.ج)", color = Color.LightGray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("صوتية 1: مدحت شلبي | صوتية 2: أيمن الكاشف", color = CyanAccent, fontSize = 12.sp)
                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("▷ شاهد البث الحي الآن", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MatchDetailPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("مركز تفاصيل المباراة والإحصائيات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("مباشر الآن", color = Color.White, modifier = Modifier.background(LiveRed, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF064E3B))))
        ) {
            Text("معاينة البث المباشر", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("إحصائيات المباراة اللحظية", color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("58% الأهلي", color = LiveRed, fontSize = 12.sp)
            Text("الاستحواذ", color = Color.Gray, fontSize = 12.sp)
            Text("42% الزمالك", color = Color.White, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.DarkGray)) {
            Box(modifier = Modifier.fillMaxWidth(0.58f).fillMaxHeight().background(LiveRed))
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("تشغيل البث المباشر (ExoPlayer)", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TopHeaderBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(end = 16.dp))
            Text("سحاب IPTV", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.background(CardBg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("• الرياضة ومباريات اليوم - تغطية 4K", color = Color.LightGray, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("• محدث تلقائياً", color = Color.Green, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.background(CardBg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("جدول الترتيب", color = Color.White, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.background(CardBg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("قائمة الهدافين", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FilterBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("• الدوري المصري الممتاز", color = Color.White, modifier = Modifier.background(PrimaryAccent, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp)
            Text("دوري أبطال أوروبا", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp)
            Text("الدوري الإنجليزي", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("الخميس - أمس", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp)
            Text("الجمعة - اليوم", color = Color.White, modifier = Modifier.background(PrimaryAccent, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp)
        }
    }
}
