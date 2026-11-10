package com.saydlawy.sahab.data.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

class MorphTvApi(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun getMatches(): ApiResult<List<Match>> {
        return suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url("https://morph-tv.com/api/matches").build()
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(ApiResult.Error("Morph TV Network Error: ${e.message}"))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val body = res.body?.string()
                        if (res.isSuccessful && !body.isNullOrBlank()) {
                            try {
                                val parsed = json.decodeFromString<MorphResponse>(body)
                                val matches = parsed.matches.mapNotNull { it.toAppMatch() }
                                if (cont.isActive) cont.resume(ApiResult.Success(matches))
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(ApiResult.Error("Morph TV Parse Error: ${e.message}"))
                            }
                        } else {
                            if (cont.isActive) cont.resume(ApiResult.Error("Morph TV HTTP Error: ${res.code}"))
                        }
                    }
                }
            })
        }
    }
}

@Serializable
data class MorphResponse(val matches: List<MorphMatch> = emptyList())

@Serializable
data class MorphMatch(
    val id: String = "",
    val kickoff: String = "",
    val status: String = "",
    val competition: String = "",
    val home: MorphTeam = MorphTeam(),
    val away: MorphTeam = MorphTeam(),
    val score: MorphScore = MorphScore()
)

@Serializable
data class MorphTeam(val id: String = "", val name: String = "", val badge: String? = null)

@Serializable
data class MorphScore(val home: Int? = null, val away: Int? = null)

fun MorphMatch.toAppMatch(): Match? {
    // تحويل حالة Morph TV إلى MatchStatus
    val statusEnum = when (status.lowercase()) {
        "live", "inprogress", "1h", "2h" -> MatchStatus.LIVE
        "finished", "ft" -> MatchStatus.FINISHED
        "postponed" -> MatchStatus.POSTPONED
        "cancelled" -> MatchStatus.CANCELLED
        else -> MatchStatus.UPCOMING
    }

    // محاولة مطابقة اسم البطولة بمعرف API-Football (للفلترة)
    val leagueId = mapCompetitionToLeagueId(competition)
    if (leagueId == null) return null // تجاهل البطولات غير المعتمدة

    return Match(
        id = id.hashCode(),
        league = League(leagueId, AllowedLeaguesConfig.translateLeagueName(leagueId), "", ""),
        homeTeam = Team(home.id.hashCode(), home.name, home.badge ?: ""),
        awayTeam = Team(away.id.hashCode(), away.name, away.badge ?: ""),
        homeScore = score.home,
        awayScore = score.away,
        status = statusEnum,
        minute = null,
        date = kickoff.split("T").firstOrNull() ?: "",
        time = kickoff.split("T").getOrNull(1)?.take(5) ?: "",
        stadium = "غير محدد",
        channel = "غير محدد",
        commentator = "غير محدد",
        stats = null
    )
}

// دالة مطابقة أسماء البطولات في Morph TV بمعرفات API-Football
private fun mapCompetitionToLeagueId(competition: String): Int? {
    return when {
        competition.contains("Egyptian Premier", ignoreCase = true) -> 233
        competition.contains("Saudi", ignoreCase = true) || competition.contains("Roshn", ignoreCase = true) -> 307
        competition.contains("CAF Champions", ignoreCase = true) || competition.contains("African Champions", ignoreCase = true) -> 12
        competition.contains("CAF Confed", ignoreCase = true) || competition.contains("Confederation", ignoreCase = true) -> 20
        competition.contains("UEFA Champions", ignoreCase = true) || competition.contains("Champions League", ignoreCase = true) && !competition.contains("Asia") -> 2
        competition.contains("UEFA Europa", ignoreCase = true) || competition.contains("Europa League", ignoreCase = true) -> 3
        competition.contains("African Nations", ignoreCase = true) || competition.contains("AFCON", ignoreCase = true) -> 6
        competition.contains("European Championship", ignoreCase = true) || competition.contains("Euro", ignoreCase = true) -> 403
        competition.contains("Asian Cup", ignoreCase = true) -> 8
        competition.contains("World Cup", ignoreCase = true) && !competition.contains("Club") -> 1
        competition.contains("Arab Cup", ignoreCase = true) -> 452
        competition.contains("English Premier", ignoreCase = true) -> 39
        competition.contains("Spanish La Liga", ignoreCase = true) || competition.contains("La Liga", ignoreCase = true) -> 140
        competition.contains("Italian Serie A", ignoreCase = true) || competition.contains("Serie A", ignoreCase = true) -> 135
        competition.contains("German Bundesliga", ignoreCase = true) || competition.contains("Bundesliga", ignoreCase = true) -> 78
        competition.contains("French Ligue 1", ignoreCase = true) || competition.contains("Ligue 1", ignoreCase = true) -> 61
        competition.contains("Turkish", ignoreCase = true) || competition.contains("Super Lig", ignoreCase = true) -> 203
        competition.contains("AFC Champions", ignoreCase = true) || competition.contains("Asian Champions", ignoreCase = true) -> 17
        else -> null
    }
}
