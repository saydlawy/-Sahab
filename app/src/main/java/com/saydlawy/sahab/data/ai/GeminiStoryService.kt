package com.saydlawy.sahab.data.ai

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

enum class ContentType { MOVIE, SERIES }

sealed class GeminiResult {
    data class Success(
        val story: String,
        val confidence: String,
        val identifiedTitle: String,
        val sources: List<String>,
    ) : GeminiResult()
    object RateLimited : GeminiResult()
    object InvalidKey : GeminiResult()
    data class Error(val message: String) : GeminiResult()
}

class GeminiStoryService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val model = "gemini-2.5-flash"

    suspend fun downloadPoster(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                if (bytes.size > 6_000_000) return@withContext null
                bytes
            }
        } catch (e: Exception) {
            Log.w("GeminiStory", "poster download failed: $e")
            null
        }
    }

    suspend fun generateStory(
        rawName: String,
        year: String,
        imdbId: String,
        posterBytes: ByteArray?,
        contentType: ContentType,
        apiKey: String,
    ): GeminiResult = withContext(Dispatchers.IO) {
        try {
            val prompt = buildPrompt(rawName, year, imdbId, contentType)
            val body = buildBody(prompt, posterBytes)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val req = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                val code = resp.code
                if (code == 429) return@use GeminiResult.RateLimited
                if (code == 401 || code == 403) return@use GeminiResult.InvalidKey
                if (code == 400) {
                    val err = resp.body?.string().orEmpty()
                    Log.e("GeminiStory", "400: $err")
                    return@use GeminiResult.Error("الطلب مرفوض — تحقق من المفتاح")
                }
                if (!resp.isSuccessful) {
                    Log.e("GeminiStory", "HTTP $code: ${resp.body?.string().orEmpty()}")
                    return@use GeminiResult.Error("خطأ في الخدمة ($code)")
                }
                val text = resp.body?.string().orEmpty()
                parseSuccess(text)
            }
        } catch (e: Exception) {
            Log.e("GeminiStory", "exception", e)
            GeminiResult.Error("تعذّر الاتصال بخدمات الذكاء الاصطناعي")
        }
    }

    private fun buildBody(prompt: String, posterBytes: ByteArray?): String {
        val root = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        if (posterBytes != null) {
                            val b64 = Base64.encodeToString(posterBytes, Base64.NO_WRAP)
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", "image/jpeg")
                                    put("data", b64)
                                }
                            }
                        }
                        addJsonObject {
                            put("text", prompt)
                        }
                    }
                }
            }
            putJsonArray("tools") {
                addJsonObject { putJsonObject("google_search") {} }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.4)
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("story") { put("type", "STRING") }
                        putJsonObject("confidence") {
                            put("type", "STRING")
                            putJsonArray("enum") {
                                add("high"); add("medium"); add("low")
                            }
                        }
                        putJsonObject("identified_title") { put("type", "STRING") }
                        putJsonObject("sources") {
                            put("type", "ARRAY")
                            putJsonObject("items") { put("type", "STRING") }
                        }
                    }
                    putJsonArray("required") {
                        add("story"); add("confidence"); add("identified_title"); add("sources")
                    }
                }
            }
        }
        return root.toString()
    }

    private fun parseSuccess(body: String): GeminiResult {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val candidates = root["candidates"]?.jsonArray
            if (candidates.isNullOrEmpty()) {
                return GeminiResult.Error("استجابة فارغة من الخدمة")
            }
            val parts = candidates[0].jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray
            var text = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            if (text.isNullOrBlank()) {
                return GeminiResult.Error("لا يوجد نص في الاستجابة")
            }
            var clean = text.trim()
            if (clean.startsWith("```")) {
                clean = clean.removePrefix("```json").removePrefix("```")
                clean = clean.removeSuffix("```").trim()
            }
            val inner = json.parseToJsonElement(clean).jsonObject
            val story = inner["story"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val confidence = inner["confidence"]?.jsonPrimitive?.contentOrNull ?: "low"
            val title = inner["identified_title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val sources = inner["sources"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            if (story.isBlank()) return GeminiResult.Error("لم يُنتج نص")
            GeminiResult.Success(story, confidence, title, sources)
        } catch (e: Exception) {
            Log.e("GeminiStory", "parse error", e)
            GeminiResult.Error("تعذّر تحليل الاستجابة")
        }
    }

    private fun buildPrompt(rawName: String, year: String, imdbId: String, type: ContentType): String {
        val yearStr = year.ifBlank { "غير معروفة" }
        val imdbStr = if (imdbId.isBlank()) "" else "\n- معرّف IMDb: $imdbId"
        return if (type == ContentType.MOVIE) {
            "أنت مساعد متخصص في التعرف على الأفلام من بياناتها البصرية والنصية.\n\n" +
                "معلومات الفيلم من المزود:\n" +
                "- الاسم (قد يكون مشوّهًا عمدًا): \"$rawName\"\n" +
                "- السنة: $yearStr$imdbStr\n" +
                "- صورة البوستر: مرفقة\n\n" +
                "مهمتك:\n" +
                "1. حدد الفيلم الحقيقي باستخدام الصورة + الاسم + السنة.\n" +
                "   الاسم قد يكون مشوّهًا (مثل Stranngers بدل Strangers).\n" +
                "2. ابحث في الويب عن معلومات الفيلم الحقيقي.\n" +
                "3. اكتب قصة عربية موجزة عنه.\n\n" +
                "قواعد صارمة:\n" +
                "- 120-180 كلمة، عربية فصحى، نبرة محايدة\n" +
                "- غطِّ: الحبكة، الشخصيات الرئيسية، الموضوع\n" +
                "- لا تذكر المخرج أو الممثلين داخل القصة\n" +
                "- لا تخترع تفاصيل غير موجودة\n" +
                "- إذا كانت الثقة منخفضة، اكتب قصة قصيرة توضح عدم اليقين\n" +
                "- استخدم البحث المدمج للتحقق"
        } else {
            "أنت مساعد متخصص في التعرف على المسلسلات من بياناتها البصرية والنصية.\n\n" +
                "معلومات المسلسل من المزود:\n" +
                "- الاسم (قد يكون مشوّهًا): \"$rawName\"\n" +
                "- سنة أول عرض: $yearStr$imdbStr\n" +
                "- صورة البوستر: مرفقة\n\n" +
                "مهمتك:\n" +
                "1. حدد المسلسل الحقيقي (ليس الفيلم) باستخدام الصورة + الاسم + السنة.\n" +
                "2. ابحث عن معلومات المسلسل الحقيقي.\n" +
                "3. اكتب قصة عربية موجزة عنه.\n\n" +
                "قواعد صارمة:\n" +
                "- 150-200 كلمة، عربية فصحى، نبرة محايدة\n" +
                "- غطِّ: المفهوم الأساسي، الشخصيات، الصراع المحوري\n" +
                "- لا تصف حلقة معينة، ولا تحرق أحداث النهاية\n" +
                "- لا تذكر المخرج أو الممثلين داخل القصة\n" +
                "- لا تخترع تفاصيل غير موجودة\n" +
                "- إذا كانت الثقة منخفضة، اكتب قصة قصيرة توضح ذلك\n" +
                "- استخدم البحث المدمج للتحقق"
        }
    }
}

sealed class AiGenerationState {
    object Idle : AiGenerationState()
    object Loading : AiGenerationState()
    object NoKey : AiGenerationState()
    object RateLimited : AiGenerationState()
    object InvalidKey : AiGenerationState()
    data class Error(val message: String) : AiGenerationState()
    data class Success(val story: String, val sources: List<String>) : AiGenerationState()
}
