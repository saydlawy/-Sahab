package com.saydlawy.sahab.data.sports

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

class EspnApi(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun getFixturesByDate(date: String, leagueId: Int): ApiResult<List<Match>> {
        // البحث عن الـ Slug المطابق لمعرف البطولة
        val slug = AllowedLeaguesConfig.ESPN_SLUG_TO_ID.entries.firstOrNull { it.value == leagueId }?.key
            ?: return ApiResult.Success(emptyList()) // البطولة غير مدعومة في ESPN
            
        val formattedDate = date.replace("-", "") // تحويل التاريخ إلى YYYYMMDD
        val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/$slug/scoreboard?dates=$formattedDate"

        return suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url(url).build()
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(ApiResult.Error(e.message ?: "ESPN Network Error"))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val body = res.body?.string()
                        if (res.isSuccessful && !body.isNullOrBlank()) {
                            try {
                                val parsed = json.decodeFromString<EspnResponse>(body)
                                val matches = parsed.events.mapNotNull { it.toAppMatch(leagueId) }
                                if (cont.isActive) cont.resume(ApiResult.Success(matches))
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(ApiResult.Error("ESPN Parse Error: ${e.message}"))
                            }
                        } else {
                            if (cont.isActive) cont.resume(ApiResult.Error("ESPN HTTP Error: ${res.code}"))
                        }
                    }
                }
            })
        }
    }
}

// --- ESPN DTOs ---
@Serializable data class EspnResponse(val events: List<EspnEvent> = emptyList())
@Serializable data class EspnEvent(val id: String = "", val date: String = "", val competitions: List<EspnCompetition> = emptyList())
@Serializable data class EspnCompetition(val competitors: List<EspnCompetitor> = emptyList(), val status: EspnStatus = EspnStatus(), val venue: EspnVenue? = null)
@Serializable data class EspnCompetitor(val id: String = "", val homeAway: String = "", val score: String? = null, val team: EspnTeam = EspnTeam())
@Serializable data class EspnTeam(val displayName: String = "", val logo: String? = null)
@Serializable data class EspnStatus(val displayClock: String? = null, val type: EspnStatusType = EspnStatusType())
@Serializable data class EspnStatusType(val state: String = "pre", val detail: String = "", val shortDetail: String = "")
@Serializable data class EspnVenue(val fullName: String? = null)

// --- Mapper ---
fun EspnEvent.toAppMatch(leagueId: Int): Match? {
    val comp = competitions.firstOrNull() ?: return null
    val home = comp.competitors.firstOrNull { it.homeAway == "home" } ?: return null
    val away = comp.competitors.firstOrNull { it.homeAway == "away" } ?: return null

    val statusEnum = when(comp.status.type.state) {
        "in" -> MatchStatus.LIVE
        "post" -> MatchStatus.FINISHED
        else -> MatchStatus.UPCOMING
    }

    return Match(
        id = id.hashCode(), // تحويل معرف ESPN النصي إلى رقم
        league = League(leagueId, AllowedLeaguesConfig.translateLeagueName(leagueId), "", ""),
        homeTeam = Team(home.id.hashCode(), home.team.displayName, home.team.logo ?: ""),
        awayTeam = Team(away.id.hashCode(), away.team.displayName, away.team.logo ?: ""),
        homeScore = home.score?.toIntOrNull(),
        awayScore = away.score?.toIntOrNull(),
        status = statusEnum,
        minute = comp.status.displayClock,
        date = date.split("T").firstOrNull() ?: "",
        time = date.split("T").getOrNull(1)?.take(5) ?: "",
        stadium = comp.venue?.fullName ?: "غير محدد",
        channel = "غير محدد", // سيتم جلبه لاحقاً من Bzzoiro
        commentator = "غير محدد",
        stats = null
    )
}
