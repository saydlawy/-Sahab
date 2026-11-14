package com.saydlawy.sahab.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.theme.Palette

@Composable
fun TrendingMoviesSection(onMovieClick: (CatalogMovieEntity) -> Unit) {
    val container = LocalContainer.current
    var movies by remember { mutableStateOf<List<CatalogMovieEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        movies = container.firebaseCatalog.getTrendingMovies(15)
    }

    if (movies.isNotEmpty()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔥 أفلام مقترحة لك", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(movies, key = { it.sahabId }) { movie ->
                    TrendingMovieCard(movie) { onMovieClick(movie) }
                }
            }
        }
    }
}

@Composable
private fun TrendingMovieCard(movie: CatalogMovieEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Glass)
        ) {
            RemoteImage(
                url = movie.posterUrl,
                fallbackText = movie.titleAr,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // شارة 4K / UHD
            if (movie.titleAr.contains("4K", true) || movie.rating > "8.0") {
                Text(
                    "4K",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        
        Text(
            movie.titleAr,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("⭐", fontSize = 10.sp)
            Text(
                if (movie.rating.isNotBlank()) movie.rating else "N/A",
                fontSize = 11.sp, color = Palette.Gold
            )
            Text("•", fontSize = 11.sp, color = Palette.TextDim)
            Text(movie.year, fontSize = 11.sp, color = Palette.TextDim)
        }
    }
}
