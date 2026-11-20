package com.saydlawy.sahab.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import com.saydlawy.sahab.data.CastMember
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.ui.theme.Palette

@Composable
fun CastMembersRow(
    members: List<CastMember>,
    modifier: Modifier = Modifier,
) {
    if (members.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = Ar.cast,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Glow,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(members, key = { "${it.name}|${it.photoUrl}" }) { member ->
                var focused by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .width(74.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Palette.CardSurfaceElevated)
                            .border(
                                width = if (focused) 2.5.dp else 1.dp,
                                color = if (focused) Palette.FocusCyan else Color.White.copy(alpha = 0.16f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (member.photoUrl.isNotBlank()) {
                            val context = LocalContext.current
                            val density = LocalDensity.current
                            val photoPx = remember(density) { with(density) { 64.dp.roundToPx() } }
                            val photoRequest = remember(context, member.photoUrl, photoPx) {
                                ImageRequest.Builder(context)
                                    .data(member.photoUrl)
                                    .size(photoPx, photoPx)
                                    .precision(Precision.INEXACT)
                                    .build()
                            }
                            AsyncImage(
                                model = photoRequest,
                                contentDescription = member.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = member.name.trim().take(1).uppercase(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.TextMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = member.name,
                        fontSize = 11.sp,
                        fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                        color = if (focused) Palette.FocusCyan else Palette.Text,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = 14.sp,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
