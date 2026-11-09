package com.saydlawy.sahab.data.sports

import com.saydlawy.sahab.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

class SportsApi(
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val apiFootballKey = BuildConfig.API_FOOTBALL_KEY

    suspend fun getLiveMatches(): List<Match> {
        if (apiFootballKey.isBlank()) return emptyList()
        val url = "https://v3.football.api-sports.io/fixtures?live=all"
        val responseText = executeRequest(url) ?: return emptyList()
        
        return withContext(Dispatchers.Default) {
            try {
                val apiResponse = json.decodeFromString<ApiFootballResponse>(responseText)
                apiResponse.response.map { it.toAppMatch() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getFixturesByDate(date: String): List<Match> {
        if (apiFootballKey.isBlank()) return emptyList()
        val url = "https://v3.football.api-sports.io/fixtures?date=$date"
        val responseText = executeRequest(url) ?: return emptyList()
        
        return withContext(Dispatchers.Default) {
            try {
                val apiResponse = json.decodeFromString<ApiFootballResponse>(responseText)
                apiResponse.response.map { it.toAppMatch() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun executeRequest(url: String): String? = suspendCancellableCoroutine { cont ->
        val request = Request.Builder()
            .url(url)
            .addHeader("x-apisports-key", apiFootballKey)
            .build()
        
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        if (cont.isActive) cont.resume(it.body?.string())
                    } else {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        })
    }
}

// --- Data Transfer Objects (DTOs) for API-Football JSON Parsing ---

@Serializable
data class ApiFootballResponse(
    val response: List<FixtureResponse> = emptyList()
)

@Serializable
data class FixtureResponse(
    val fixture: FixtureInfo,
    val league: LeagueInfo,
    val teams: TeamsInfo,
    val goals: GoalsInfo
)

@Serializable
data class FixtureInfo(
    val id: Int,
    val date: String,
    val status: StatusInfo,
    val venue: VenueInfo? = null
)

@Serializable
data class StatusInfo(
    val long: String,
    val short: String,
    val elapsed: Int? = null
)

@Serializable
data class VenueInfo(
    val name: String? = null
)

@Serializable
data class LeagueInfo(
    val id: Int,
    val name: String,
    val country: String
)

@Serializable
data class TeamsInfo(
    val home: TeamInfo,
    val away: TeamInfo
)

@Serializable
data class TeamInfo(
    val id: Int,
    val name: String,
    val logo: String
)

@Serializable
data class GoalsInfo(
    val home: Int? = null,
    val away: Int? = null
)

// --- Mapper Function ---
fun FixtureResponse.toAppMatch(): Match {
    val statusEnum = when (fixture.status.short) {
        "1H", "2H", "HT", "ET", "P", "LIVE" -> MatchStatus.LIVE
        "FT", "AET", "PEN" -> MatchStatus.FINISHED
        "PST" -> MatchStatus.POSTPONED
        "CANC" -> MatchStatus.CANCELLED
        else -> MatchStatus.UPCOMING
    }

    return Match(
        id = fixture.id,
        league = League(id = league.id, name = league.name, country = league.country),
        homeTeam = Team(id = teams.home.id, name = teams.home.name, logoUrl = teams.home.logo),
        awayTeam = Team(id = teams.away.id, name = teams.away.name, logoUrl = teams.away.logo),
        homeScore = goals.home,
        awayScore = goals.away,
        status = statusEnum,
        minute = fixture.status.elapsed?.toString(),
        date = fixture.date.split("T").firstOrNull() ?: "",
        time = fixture.date.split("T").lastOrNull()?.take(5) ?: "",
        stadium = fixture.venue?.name ?: "غير محدد",
        channel = "قناة غير معروفة", // سيتم جلبها لاحقاً من Bzzoiro
        commentator = "غير محدد",
        stats = null // الإحصائيات تحتاج طلب API منفصل
    )
}
