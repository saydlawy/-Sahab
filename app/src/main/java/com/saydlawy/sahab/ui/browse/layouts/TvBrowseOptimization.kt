package com.saydlawy.sahab.ui.browse.layouts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.ui.browse.FocusedPosterDetails
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvPivotScrollContainer(
    parentFraction: Float = 0.12f,
    childFraction: Float = 0f,
    content: @Composable () -> Unit,
) {
    val pivotSpec = remember(parentFraction, childFraction) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val targetPosition = containerSize * parentFraction - (size * childFraction)
                return offset - targetPosition
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec, content = content)
}

@Composable
internal fun rememberDebouncedHero(
    details: FocusedPosterDetails?,
    delayMs: Long = 220L,
): FocusedPosterDetails? {
    var settled by remember { mutableStateOf(details) }
    LaunchedEffect(details?.poster?.id, details?.backdropUrl, details?.plot) {
        if (details == null) {
            settled = null
        } else {
            delay(delayMs)
            settled = details
        }
    }
    return settled
}

@Composable
internal fun ContinueWatchingProgressBar(
    positionMs: Long,
    durationMs: Long,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0L || positionMs <= 0L) return
    val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    if (progress !in 0.03f..0.95f) return
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(accentColor),
        )
    }
}
