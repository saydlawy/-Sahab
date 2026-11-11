package com.saydlawy.sahab.data.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Client for Goal API (`https://api.goal-api.com/v1`), used strictly by background sync
 * (`MonthlySyncWorker`) to populate and refresh local Room tables.
 */
class GoalApi(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fixturesByLeague(
        league: GoalLeagueSpec,
        maxPages: Int = 5,
    ): ApiResult<List<MatchEntity>> {
        if (apiKey.isBlank()) {
            return ApiResult.Error("GOAL_API_KEY غير متوفر")
        }
        val collected = mutableListOf<MatchEntity>()
        val nowMs = System.currentTimeMillis()
        for (page in 1..maxPages) {
            val url = "${baseUrl.trimEnd('/')}/leagues/${league.nanoid}/fixtures?page=$page&limit=$PAGE_SIZE"
            val root = when (val res = fetchJson(url)) {
                is ApiResult.Error -> return if (collected.isNotEmpty()) ApiResult.Success(collected) else res
                is ApiResult.Success -> res.data
            }
            val arr = extractArray(root, "fixtures")
            if (arr.isEmpty()) break

            val mapped = withContext(Dispatchers.Default) {
                arr.mapNotNull { elem -> elem.asObj()?.let { parseFixture(it, league, nowMs) } }
            }
            collected.addAll(mapped)

            val pagination = root.asObj()?.get("pagination").asObj() ?: root.asObj()?.get("meta").asObj()
            val hasMore = pagination?.bool("hasMore") ?: (arr.size >= PAGE_SIZE)
            if (!hasMore) break
        }
        return ApiResult.Success(collected.distinctBy { it.id })
    }

    suspend fun standingsByLeague(league: GoalLeagueSpec): ApiResult<List<StandingEntity>> {
        if (apiKey.isBlank()) {
            return ApiResult.Error("GOAL_API_KEY غير متوفر")
        }
        val url = "${baseUrl.trimEnd('/')}/leagues/${league.nanoid}/standings"
        val root = when (val res = fetchJson(url)) {
            is ApiResult.Error -> return res
            is ApiResult.Success -> res.data
        }
        val arr = extractArray(root, "standings")
        val rows = withContext(Dispatchers.Default) {
            arr.mapIndexedNotNull { idx, elem ->
                elem.asObj()?.let { parseStanding(it, league.leagueId, idx + 1) }
            }.filter { it.teamName.isNotBlank() }
        }
        return ApiResult.Success(rows)
    }

    suspend fun currentSeason(nanoid: String): String? {
        if (apiKey.isBlank()) return null
        val url = "${baseUrl.trimEnd('/')}/leagues/$nanoid"
        val res = fetchJson(url) as? ApiResult.Success ?: return null
        val obj = res.data.asObj()?.get("data").asObj() ?: res.data.asObj() ?: return null
        return obj.str("season").ifBlank { obj.str("leagueYear") }.ifBlank { null }
    }

    private fun parseFixture(obj: JsonObject, league: GoalLeagueSpec, savedAtMs: Long): MatchEntity? {
        val rawId = obj.str("id").ifBlank { return null }
        val homeObj = obj["homeTeam"].asObj()
        val awayObj = obj["awayTeam"].asObj()
        val homeName = obj.str("homeTeamName").ifBlank { homeObj?.str("name").orEmpty() }.trim()
        val awayName = obj.str("awayTeamName").ifBlank { awayObj?.str("name").orEmpty() }.trim()
        if (homeName.isBlank() || awayName.isBlank()) return null

        val kickoff = obj.str("kickoffUtc").ifBlank { obj.str("kickoff_utc") }.ifBlank { obj.str("date") }
        val date = kickoff.substringBefore('T').takeIf { it.length >= 10 } ?: ""
        val time = kickoff.substringAfter('T', "").take(5).ifBlank { "17:00" }

        val liveFlag = obj.bool("matchLive") ?: false
        val rawStatus = obj.str("matchStatus").ifBlank { obj.str("status") }
        val statusEnum = mapStatus(rawStatus, liveFlag)

        val homeScore = obj.int("homeTeamScore") ?: obj.int("homeScore")
        val awayScore = obj.int("awayTeamScore") ?: obj.int("awayScore")
        val minute = obj.str("matchElapsed").ifBlank { obj.str("minute") }.ifBlank { null }
        val stadium = obj.str("stadium").ifBlank { obj.str("venue") }.ifBlank { "غير محدد" }
        val homeLogo = obj.str("homeTeamLogo").ifBlank { homeObj?.str("logo").orEmpty() }
        val awayLogo = obj.str("awayTeamLogo").ifBlank { awayObj?.str("logo").orEmpty() }

        return MatchEntity(
            id = "${league.leagueId}_$rawId".stableId(),
            leagueId = league.leagueId,
            leagueName = league.arabicName,
            leagueCountry = league.country,
            homeTeamId = homeName.stableId(),
            homeTeamName = homeName,
            homeTeamLogo = homeLogo,
            awayTeamId = awayName.stableId(),
            awayTeamName = awayName,
            awayTeamLogo = awayLogo,
            homeScore = homeScore,
            awayScore = awayScore,
            status = statusEnum.name,
            minute = minute,
            date = date,
            time = time,
            stadium = stadium,
            channel = "غير محدد",
            commentator = "غير محدد",
            savedAt = savedAtMs,
        )
    }

    private fun parseStanding(obj: JsonObject, leagueId: Int, fallbackRank: Int): StandingEntity? {
        val teamObj = obj["team"].asObj()
        val teamName = obj.str("teamName").ifBlank { teamObj?.str("name").orEmpty() }.trim()
        if (teamName.isBlank()) return null
        val rank = obj.int("position") ?: obj.int("rank") ?: fallbackRank
        val played = obj.int("played") ?: 0
        val win = obj.int("won") ?: obj.int("win") ?: 0
        val draw = obj.int("drawn") ?: obj.int("draw") ?: 0
        val loss = obj.int("lost") ?: obj.int("loss") ?: 0
        val gf = obj.int("goalsFor") ?: 0
        val ga = obj.int("goalsAgainst") ?: 0
        val gd = obj.int("goalDifference") ?: (gf - ga)
        val pts = obj.int("points") ?: 0
        return StandingEntity(
            leagueId = leagueId,
            rank = rank,
            teamName = teamName,
            played = played,
            win = win,
            draw = draw,
            loss = loss,
            goalsFor = gf,
            goalsAgainst = ga,
            goalDifference = gd,
            points = pts,
        )
    }

    private fun mapStatus(raw: String, liveFlag: Boolean): MatchStatus {
        if (liveFlag) return MatchStatus.LIVE
        return when (raw.trim().uppercase(Locale.US)) {
            "LIVE", "IN_PLAY", "1H", "2H", "HT", "ET", "FIRST_HALF", "SECOND_HALF", "HALF_TIME" -> MatchStatus.LIVE
            "FINISHED", "FT", "AET", "PEN", "AFTER_PEN", "AFTER_ET", "AWARDED" -> MatchStatus.FINISHED
            "POSTPONED", "PST", "SUSP", "SUSPENDED" -> MatchStatus.POSTPONED
            "CANCELLED", "CANC", "ABD", "ABANDONED", "WO" -> MatchStatus.CANCELLED
            else -> MatchStatus.UPCOMING
        }
    }

    private fun extractArray(root: JsonElement, nestedKey: String): JsonArray {
        if (root is JsonArray) return root
        val obj = root.asObj() ?: return JsonArray(emptyList())
        val dataElem = obj["data"]
        if (dataElem is JsonArray) return dataElem
        val dataObj = dataElem.asObj()
        if (dataObj != null) {
            (dataObj[nestedKey] as? JsonArray)?.let { return it }
            (dataObj["items"] as? JsonArray)?.let { return it }
        }
        return (obj[nestedKey] as? JsonArray) ?: JsonArray(emptyList())
    }

    private suspend fun fetchJson(url: String): ApiResult<JsonElement> {
        val raw = executeGet(url)
        return when (raw) {
            is ApiResult.Error -> raw
            is ApiResult.Success -> withContext(Dispatchers.Default) {
                runCatching { json.parseToJsonElement(raw.data) }.fold(
                    onSuccess = { elem ->
                        val ok = elem.asObj()?.bool("success")
                        if (ok == false) {
                            val errMsg = elem.asObj()?.get("error").asObj()?.str("message")
                                ?.takeIf { it.isNotBlank() } ?: "Goal API error"
                            ApiResult.Error(errMsg)
                        } else {
                            ApiResult.Success(elem)
                        }
                    },
                    onFailure = { ApiResult.Error("خطأ في تحليل بيانات Goal API") },
                )
            }
        }
    }

    private suspend fun executeGet(url: String): ApiResult<String> = suspendCancellableCoroutine { cont ->
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Accept", "application/json")
            .build()
        val call = client.newCall(req)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume(ApiResult.Error("فشل الاتصال بـ Goal API: ${e.message ?: "network"}"))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful && body.isNotBlank()) {
                        if (cont.isActive) cont.resume(ApiResult.Success(body))
                    } else {
                        if (cont.isActive) cont.resume(ApiResult.Error("HTTP ${res.code}"))
                    }
                }
            }
        })
    }

    private fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

    private fun JsonObject.str(key: String): String {
        val elem = this[key] ?: return ""
        if (elem is JsonNull) return ""
        val prim = elem as? JsonPrimitive ?: return ""
        return prim.content
    }

    private fun JsonObject.int(key: String): Int? {
        val elem = this[key] ?: return null
        if (elem is JsonNull) return null
        val prim = elem as? JsonPrimitive ?: return null
        return prim.intOrNull ?: prim.content.toIntOrNull()
    }

    private fun JsonObject.bool(key: String): Boolean? {
        val elem = this[key] ?: return null
        val prim = elem as? JsonPrimitive ?: return null
        return prim.booleanOrNull
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.goal-api.com/v1"
        private const val PAGE_SIZE = 100
    }
}
