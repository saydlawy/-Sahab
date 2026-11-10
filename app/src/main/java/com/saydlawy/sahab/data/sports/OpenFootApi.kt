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

class OpenFootApi(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val baseUrl = "https://openfootapi.com/v1"

    suspend fun getMatches(apiKey: String, competitionId: String, from: String, to: String): ApiResult<List<Match>> {
        if (apiKey.isBlank()) return ApiResult.Error("Missing OpenFootAPI Key")

        val urlBuilder = "$baseUrl/matches".toHttpUrl().newBuilder()
            .addQueryParameter("competition", competitionId)
            .addQueryParameter("from", from)
            .addQueryParameter("to", to)
            .addQueryParameter("status", "scheduled")
            .addQueryParameter("limit", "100")

        return suspendCancellableCoroutine { cont ->
            val request = Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Authorization", "Bearer $apiKey")
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
                                val parsed = json.decodeFromString<OpenFootResponse>(body)
                                val matches = parsed.data.mapNotNull { it.toAppMatch(competitionId) }
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

// --- DTOs ---
@Serializable
data class OpenFootResponse(val data: List<OpenFootMatch> = emptyList())

@Serializable
data class OpenFootMatch(
    val id: String = "",
    @SerialName("homeTeam") val homeTeam: OpenFootTeam = OpenFootTeam(),
    @SerialName("awayTeam") val awayTeam: OpenFootTeam = OpenFootTeam(),
    @SerialName("kickoffAt") val kickoffAt: String = "",
    @SerialName("status") val status: String = "scheduled"
)

@Serializable
data class OpenFootTeam(
    val id: String = "",
    val name: String = "",
    val logo: String? = null
)

// --- Mapper ---
fun OpenFootMatch.toAppMatch(competitionId: String): Match? {
    val leagueId = when (competitionId) {
        "comp_egyptian_prem_egy" -> 233
        "comp_saudi_pro_league_sau" -> 307
        "comp_uefa_champions_league" -> 2
        "comp_uefa_europa_league" -> 3
        "comp_uefa_conference_league" -> 848
        "comp_fifa_world_cup" -> 1
        "comp_premier_league_eng" -> 39
        "comp_laliga_es" -> 140
        "comp_serie_a_it" -> 135
        "comp_bundesliga_de" -> 78
        "comp_ligue_1_fr" -> 61
        else -> return null
    }

    val datePart = if (kickoffAt.length >= 10) kickoffAt.take(10) else ""
    val timePart = if (kickoffAt.length >= 16) kickoffAt.substring(11, 16) else ""

    return Match(
        id = id.ifBlank { "${leagueId}_${homeTeam.name}_${awayTeam.name}_$datePart" }.stableId(),
        league = League(leagueId, AllowedLeaguesConfig.translateLeagueName(leagueId), "", ""),
        homeTeam = Team(homeTeam.id.ifBlank { homeTeam.name }.stableId(), homeTeam.name, homeTeam.logo ?: ""),
        awayTeam = Team(awayTeam.id.ifBlank { awayTeam.name }.stableId(), awayTeam.name, awayTeam.logo ?: ""),
        homeScore = null,
        awayScore = null,
        status = MatchStatus.UPCOMING,
        minute = null,
        date = datePart,
        time = timePart,
        stadium = "غير محدد",
        channel = "غير محدد",
        commentator = "غير محدد",
        stats = null
    )
}
