package com.saydlawy.sahab.ui.detail

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.ai.AiGenerationState
import com.saydlawy.sahab.data.ai.ContentType
import com.saydlawy.sahab.data.ai.GeminiResult
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

/**
 * O-01 / O-02: Generates an AI story using [ApiKeyManager.orderedKeysForRotation],
 * automatically rotating to the next key on HTTP 429 until one succeeds or all keys are exhausted.
 */
internal suspend fun generateStoryWithKeyRotation(
    container: AppContainer,
    rawName: String,
    year: String,
    imdbId: String,
    posterUrl: String,
    contentType: ContentType,
): GeminiResult? {
    val keys = container.aiKeys.orderedKeysForRotation()
    if (keys.isEmpty()) return null
    val posterBytes = container.gemini.downloadPoster(posterUrl)
    var lastResult: GeminiResult = GeminiResult.RateLimited
    for (key in keys) {
        val result = container.gemini.generateStory(
            rawName = rawName,
            year = year,
            imdbId = imdbId,
            posterBytes = posterBytes,
            contentType = contentType,
            apiKey = key.value,
        )
        container.aiKeys.markUsed(key.id)
        when (result) {
            is GeminiResult.RateLimited -> {
                container.aiKeys.mark429(key.id)
                lastResult = result
            }
            is GeminiResult.Success -> {
                container.aiKeys.setActive(key.id)
                return result
            }
            else -> return result
        }
    }
    return lastResult
}

@Composable
internal fun AiStoryPlotSection(
    plot: String,
    emptyLabel: String,
    aiState: AiGenerationState,
    onGenerateStory: () -> Unit,
    plotFontSizeSp: Int = 15,
    plotLineHeightSp: Int = 25,
) {
    DotTitle(Ar.plot, Palette.Gold, fontSize = 16.sp)
    Spacer(Modifier.height(8.dp))
    if (plot.isNotBlank()) {
        Text(
            text = plot,
            fontSize = plotFontSizeSp.sp,
            lineHeight = plotLineHeightSp.sp,
            color = Palette.Text.copy(alpha = 0.90f),
        )
    } else {
        when (aiState) {
            is AiGenerationState.Loading -> Text("جاري توليد القصة...", color = Palette.Glow, fontSize = 14.sp)
            is AiGenerationState.Error -> {
                Text(aiState.message, color = Palette.Danger, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                GlassButton("إعادة المحاولة", onGenerateStory, primary = true)
            }
            is AiGenerationState.RateLimited -> Text(
                "الخدمة غير متاحة مؤقتًا — استُنفدت المفاتيح المتاحة.",
                color = Palette.Gold,
                fontSize = 13.sp,
            )
            is AiGenerationState.InvalidKey -> Text("المفتاح غير صالح — تحقق من الإعدادات.", color = Palette.Danger, fontSize = 13.sp)
            is AiGenerationState.NoKey -> Text("أضف مفتاح Gemini من الإعدادات.", color = Palette.Gold, fontSize = 13.sp)
            else -> {
                Text(emptyLabel, color = Palette.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                GlassButton("توليد القصة بالذكاء الاصطناعي", onGenerateStory, primary = true)
            }
        }
    }
}
