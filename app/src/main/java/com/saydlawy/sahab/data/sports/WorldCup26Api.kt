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

class WorldCup26Api(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun getFixtures(slug: String, from: String, to: String): ApiResult<List<Match>> {
        // from/to should be YYYYMMDD
        val url = "https://worldcup26.ir/get/soccer/$slug/fixtures?from=$from&to=$to&limit=1000"
        
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
                                val parsed = json.decodeFromString<WorldCup26Response>(body)
                                val matches = parsed.events.mapNotNull { it.toAppMatch(slug) }
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
data class WorldCup26Response(val events: List<WorldCup26Event> = emptyList())

@Serializable
data class WorldCup26Event(
    val id: String = "",
    val date: String = "",
    val competitions: List<WorldCup26Competition> = emptyList()
)

@Serializable
data class WorldCup26Competition(
    val competitors: List<WorldCup26Competitor> = emptyList(),
    val status: WorldCup26Status = WorldCup26Status(),
    val venue: WorldCup26Venue? = null
)

@Serializable
data class WorldCup26Competitor(
    val id: String = "",
    val homeAway: String = "",
    val score: String? = null,
    val team: WorldCup26Team = WorldCup26Team()
)

@Serializable
data class WorldCup26Team(
    val id: String = "",
    val displayName: String = "",
    val logo: String? = null
)

@Serializable
data class WorldCup26Status(val type: WorldCup26StatusType = WorldCup26StatusType())

@Serializable
data class WorldCup26StatusType(val state: String = "pre")

@Serializable
data class WorldCup26Venue(val fullName: String? = null)

// --- Mapper ---
fun WorldCup26Event.toAppMatch(slug: String): Match? {
    val comp = competitions.firstOrNull() ?: return null
    val home = comp.competitors.firstOrNull { it.homeAway == "home" } ?: return null
    val away = comp.competitors.firstOrNull { it.homeAway == "away" } ?: return null

    val lid = if (slug == "eng.1") 39 else if (slug == "esp.1") 140 else 0
    if (lid == 0) return null

    val statusEnum = when (comp.status.type.state) {
        "in" -> MatchStatus.LIVE
        "post" -> MatchStatus.FINISHED
        else -> MatchStatus.UPCOMING
    }

    return Match(
        id = id.hashCode(),
        league = League(lid, AllowedLeaguesConfig.translateLeagueName(lid), "", ""),
        homeTeam = Team(home.team.id.hashCode(), home.team.displayName, home.team.logo ?: ""),
        awayTeam = Team(away.team.id.hashCode(), away.team.displayName, away.team.logo ?: ""),
        homeScore = home.score?.toIntOrNull(),
        awayScore = away.score?.toIntOrNull(),
        status = statusEnum,
        minute = null,
        date = date.take(10),
        time = if (date.length > 11) date.substring(11, 16) else "",
        stadium = comp.venue?.fullName ?: "غير محدد",
        channel = "غير محدد",
        commentator = "غير محدد",
        stats = null
    )
}
