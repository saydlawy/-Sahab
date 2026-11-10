package com.saydlawy.sahab.data.sports

import kotlinx.coroutines.suspendCancellableCoroutine
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

class TheSportsDbApi(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val baseUrl = "https://www.thesportsdb.com/api/v1/json/123"

    // جلب المباريات القادمة لفريق معين
    suspend fun getNextTeamEvents(teamId: String): ApiResult<List<Match>> {
        val url = "$baseUrl/eventsnext.php?id=$teamId"
        return fetchTeamEvents(url)
    }

    // جلب المباريات السابقة (النتائج) لفريق معين
    suspend fun getLastTeamEvents(teamId: String): ApiResult<List<Match>> {
        val url = "$baseUrl/eventslast.php?id=$teamId"
        return fetchTeamEvents(url)
    }

    // جلب تفاصيل الفريق والشعار
    suspend fun getTeamDetails(teamId: String): ApiResult<Team> {
        val url = "$baseUrl/lookupteam.php?id=$teamId"
        return suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url(url).build()
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(ApiResult.Error(e.message ?: "Network Error"))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val body = res.body?.string()
                        if (res.isSuccessful && !body.isNullOrBlank()) {
                            try {
                                val parsed = json.decodeFromString<TeamResponse>(body)
                                val team = parsed.teams?.firstOrNull()?.toAppTeam()
                                if (cont.isActive) {
                                    if (team != null) cont.resume(ApiResult.Success(team))
                                    else cont.resume(ApiResult.Error("Team not found"))
                                }
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

    // جلب جدول الترتيب لدوري معين
    suspend fun getStandings(leagueId: String, season: String): ApiResult<List<Standing>> {
        val url = "$baseUrl/lookuptable.php?l=$leagueId&s=$season"
        return suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url(url).build()
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(ApiResult.Error(e.message ?: "Network Error"))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val body = res.body?.string()
                        if (res.isSuccessful && !body.isNullOrBlank()) {
                            try {
                                val parsed = json.decodeFromString<StandingsResponse>(body)
                                val standings = parsed.table?.map { it.toAppStanding() } ?: emptyList()
                                if (cont.isActive) cont.resume(ApiResult.Success(standings))
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

    private suspend fun fetchTeamEvents(url: String): ApiResult<List<Match>> = suspendCancellableCoroutine { cont ->
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume(ApiResult.Error(e.message ?: "Network Error"))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    val body = res.body?.string()
                    if (res.isSuccessful && !body.isNullOrBlank()) {
                        try {
                            val parsed = json.decodeFromString<EventsResponse>(body)
                            val matches = parsed.events?.mapNotNull { it.toAppMatch() } ?: emptyList()
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

// --- DTOs for TheSportsDB ---
@Serializable
data class EventsResponse(@SerialName("events") val events: List<EventDto>? = null)

@Serializable
data class EventDto(
    @SerialName("idEvent") val idEvent: String = "",
    @SerialName("strEvent") val strEvent: String? = null,
    @SerialName("strHomeTeam") val strHomeTeam: String? = null,
    @SerialName("strAwayTeam") val strAwayTeam: String? = null,
    @SerialName("dateEvent") val dateEvent: String? = null,
    @SerialName("strTime") val strTime: String? = null,
    @SerialName("intHomeScore") val intHomeScore: String? = null,
    @SerialName("intAwayScore") val intAwayScore: String? = null,
    @SerialName("strThumb") val strThumb: String? = null,
    @SerialName("idHomeTeam") val idHomeTeam: String? = null,
    @SerialName("idAwayTeam") val idAwayTeam: String? = null,
    @SerialName("strLeague") val strLeague: String? = null
)

@Serializable
data class TeamResponse(@SerialName("teams") val teams: List<TeamDto>? = null)

@Serializable
data class TeamDto(
    @SerialName("idTeam") val idTeam: String = "",
    @SerialName("strTeam") val strTeam: String? = null,
    @SerialName("strTeamBadge") val strTeamBadge: String? = null,
    @SerialName("strDescriptionEN") val strDescriptionEN: String? = null
)

@Serializable
data class StandingsResponse(@SerialName("table") val table: List<StandingDto>? = null)

@Serializable
data class StandingDto(
    @SerialName("intRank") val intRank: String? = null,
    @SerialName("strTeam") val strTeam: String? = null,
    @SerialName("intPlayed") val intPlayed: String? = null,
    @SerialName("intWin") val intWin: String? = null,
    @SerialName("intDraw") val intDraw: String? = null,
    @SerialName("intLoss") val intLoss: String? = null,
    @SerialName("intGoalsFor") val intGoalsFor: String? = null,
    @SerialName("intGoalsAgainst") val intGoalsAgainst: String? = null,
    @SerialName("intGoalDifference") val intGoalDifference: String? = null,
    @SerialName("intPoints") val intPoints: String? = null
)

// --- Mappers ---
fun EventDto.toAppMatch(): Match? {
    val home = strHomeTeam ?: return null
    val away = strAwayTeam ?: return null
    val leagueId = 233 // Default, should be mapped based on strLeague if needed
    return Match(
        id = idEvent.hashCode(),
        league = League(leagueId, strLeague ?: "Unknown", "", ""),
        homeTeam = Team(idHomeTeam?.hashCode() ?: 0, home, ""),
        awayTeam = Team(idAwayTeam?.hashCode() ?: 0, away, ""),
        homeScore = intHomeScore?.toIntOrNull(),
        awayScore = intAwayScore?.toIntOrNull(),
        status = if (intHomeScore != null) MatchStatus.FINISHED else MatchStatus.UPCOMING,
        minute = null,
        date = dateEvent ?: "",
        time = strTime?.take(5) ?: "",
        stadium = "غير محدد",
        channel = "غير محدد",
        commentator = "غير محدد",
        stats = null
    )
}

fun TeamDto.toAppTeam(): Team {
    return Team(idTeam.hashCode(), strTeam ?: "Unknown", strTeamBadge ?: "")
}

data class Standing(
    val rank: Int,
    val teamName: String,
    val played: Int,
    val win: Int,
    val draw: Int,
    val loss: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val points: Int
)

fun StandingDto.toAppStanding(): Standing {
    return Standing(
        rank = intRank?.toIntOrNull() ?: 0,
        teamName = strTeam ?: "Unknown",
        played = intPlayed?.toIntOrNull() ?: 0,
        win = intWin?.toIntOrNull() ?: 0,
        draw = intDraw?.toIntOrNull() ?: 0,
        loss = intLoss?.toIntOrNull() ?: 0,
        goalsFor = intGoalsFor?.toIntOrNull() ?: 0,
        goalsAgainst = intGoalsAgainst?.toIntOrNull() ?: 0,
        goalDifference = intGoalDifference?.toIntOrNull() ?: 0,
        points = intPoints?.toIntOrNull() ?: 0
    )
}
