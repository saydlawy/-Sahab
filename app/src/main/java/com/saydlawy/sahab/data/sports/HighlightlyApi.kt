package com.saydlawy.sahab.data.sports

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

class HighlightlyApi(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val baseUrl = "https://soccer.highlightly.net/matches"

    suspend fun getMatches(apiKey: String, countryCode: String, from: String, to: String): ApiResult<List<Match>> {
        if (apiKey.isBlank()) return ApiResult.Error("Missing Highlightly API Key")

        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("countryCode", countryCode)
            .addQueryParameter("date", from) // Highlightly relies heavily on date
            .addQueryParameter("limit", "100")

        return suspendCancellableCoroutine { cont ->
            val request = Request.Builder()
                .url(urlBuilder.build())
                .addHeader("x-rapidapi-key", apiKey)
                .addHeader("Accept", "application/json")
                .build()

            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(ApiResult.Error("Network Error: ${e.message}"))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val body = res.body?.string()
                        if (res.isSuccessful && !body.isNullOrBlank()) {
                            try {
                                val parsed = json.decodeFromString<HighlightlyResponse>(body)
                                val matches = parsed.data.mapNotNull { it.toAppMatch() }
                                if (cont.isActive) cont.resume(ApiResult.Success(matches))
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(ApiResult.Error("Parse Error: ${e.message}"))
                            }
                        } else {
                            if (cont.isActive) cont.resume(ApiResult.Error("HTTP Error: ${res.code}"))
                        }
                    }
                }
            })
        }
    }
}

@Serializable
data class HighlightlyResponse(val data: List<HighlightlyMatch> = emptyList())

@Serializable
data class HighlightlyMatch(
    val id: Int = 0,
    val date: String = "",
    val time: String = "",
    @SerialName("homeTeam") val homeTeam: HighlightlyTeam = HighlightlyTeam(),
    @SerialName("awayTeam") val awayTeam: HighlightlyTeam = HighlightlyTeam(),
    val state: HighlightlyState = HighlightlyState()
)

@Serializable
data class HighlightlyTeam(
    val id: Int = 0,
    val name: String = "",
    val logo: String? = null
)

@Serializable
data class HighlightlyState(
    val description: String = ""
)

fun HighlightlyMatch.toAppMatch(): Match? {
    if (!state.description.contains("Not started", ignoreCase = true) && !state.description.contains("To be announced", ignoreCase = true)) {
        return null // Only keep future matches
    }

    val safeId = if (id > 0) id else "hl_${homeTeam.name}_${awayTeam.name}_$date".stableId()
    val safeHomeId = if (homeTeam.id > 0) homeTeam.id else homeTeam.name.stableId()
    val safeAwayId = if (awayTeam.id > 0) awayTeam.id else awayTeam.name.stableId()

    return Match(
        id = safeId,
        league = League(233, "الدوري المصري الممتاز", "مصر", ""),
        homeTeam = Team(safeHomeId, homeTeam.name, homeTeam.logo ?: ""),
        awayTeam = Team(safeAwayId, awayTeam.name, awayTeam.logo ?: ""),
        homeScore = null,
        awayScore = null,
        status = MatchStatus.UPCOMING,
        minute = null,
        date = date,
        time = time,
        stadium = "غير محدد",
        channel = "غير محدد",
        commentator = "غير محدد",
        stats = null
    )
}
