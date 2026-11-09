package com.saydlawy.sahab.data.sports

import com.saydlawy.sahab.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

class SportsApi(private val client: OkHttpClient) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val apiFootballKey: String = try {
        BuildConfig.API_FOOTBALL_KEY
    } catch (e: Exception) {
        ""
    }

    suspend fun getLiveMatches(): ApiResult<List<Match>> {
        val url = "https://v3.football.api-sports.io/fixtures?live=all"
        return fetchFixtures(url)
    }

    suspend fun getFixturesByDate(date: String): ApiResult<List<Match>> {
        val url = "https://v3.football.api-sports.io/fixtures?date=$date"
        return fetchFixtures(url)
    }

    private suspend fun fetchFixtures(url: String): ApiResult<List<Match>> {
        if (apiFootballKey.isBlank() || apiFootballKey == "null") {
            return ApiResult.Error("مفتاح API_FOOTBALL_KEY غير موجود أو فارغ في BuildConfig. تأكد من إعدادات GitHub Secrets.")
        }

        val networkResult = executeRequest(url)
        return when (networkResult) {
            is ApiResult.Error -> networkResult
            is ApiResult.Success -> {
                withContext(Dispatchers.Default) {
                    try {
                        val parsed = json.decodeFromString<ApiFootballResponse>(networkResult.data)

                        val apiErrorMsg = extractApiErrors(parsed.errors)
                        if (apiErrorMsg != null) {
                            return@withContext ApiResult.Error("رفض المصدر (API-Football):\n$apiErrorMsg")
                        }

                        val matches = parsed.response.map { it.toAppMatch() }
                        ApiResult.Success(matches)
                    } catch (e: Exception) {
                        ApiResult.Error("خطأ في قراءة البيانات (JSON Parsing):\n${e.localizedMessage ?: "خطأ غير معروف"}")
                    }
                }
            }
        }
    }

    private fun extractApiErrors(errorsElement: JsonElement?): String? {
        if (errorsElement == null) return null
        if (errorsElement is JsonObject && errorsElement.isNotEmpty()) {
            return errorsElement.entries.joinToString("\n") { (key, value) ->
                val text = try { value.jsonPrimitive.content } catch (e: Exception) { value.toString() }
                "• $key: $text"
            }
        }
        return null
    }

    private suspend fun executeRequest(url: String): ApiResult<String> = suspendCancellableCoroutine { cont ->
        val request = Request.Builder()
            .url(url)
            .addHeader("x-apisports-key", apiFootballKey.trim())
            .build()

        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) {
                    cont.resume(ApiResult.Error("فشل الاتصال بالإنترنت أو الخادم:\n${e.localizedMessage ?: "تحقق من اتصالك بالشبكة"}"))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    val bodyString = res.body?.string()
                    if (res.isSuccessful && !bodyString.isNullOrBlank()) {
                        if (cont.isActive) cont.resume(ApiResult.Success(bodyString))
                    } else {
                        if (cont.isActive) {
                            cont.resume(ApiResult.Error("خطأ من الخادم: كود الاستجابة HTTP ${res.code}"))
                        }
                    }
                }
            }
        })
    }
}

@Serializable
data class ApiFootballResponse(
    val errors: JsonElement? = null,
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
    val id: Int = 0,
    val date: String = "",
    val status: StatusInfo = StatusInfo(),
    val venue: VenueInfo? = null
)

@Serializable
data class StatusInfo(
    val long: String? = null,
    val short: String? = null,
    val elapsed: Int? = null
)

@Serializable
data class VenueInfo(
    val name: String? = null
)

@Serializable
data class LeagueInfo(
    val id: Int = 0,
    val name: String = "بطولة غير معروفة",
    val country: String? = null,
    val logo: String? = null
)

@Serializable
data class TeamsInfo(
    val home: TeamInfo,
    val away: TeamInfo
)

@Serializable
data class TeamInfo(
    val id: Int = 0,
    val name: String = "",
    val logo: String? = null
)

@Serializable
data class GoalsInfo(
    val home: Int? = null,
    val away: Int? = null
)

fun FixtureResponse.toAppMatch(): Match {
    val statusEnum = when (fixture.status.short) {
        "1H", "2H", "HT", "ET", "BT", "P", "LIVE" -> MatchStatus.LIVE
        "FT", "AET", "PEN" -> MatchStatus.FINISHED
        "PST", "SUSP", "INT" -> MatchStatus.POSTPONED
        "CANC", "ABD", "AWD", "WO" -> MatchStatus.CANCELLED
        else -> MatchStatus.UPCOMING
    }

    val rawDate = fixture.date.split("T").firstOrNull() ?: ""
    val rawTime = fixture.date.split("T").getOrNull(1)?.take(5) ?: ""

    return Match(
        id = fixture.id,
        league = League(
            id = league.id,
            name = translateLeagueName(league.id, league.name),
            country = league.country ?: "",
            logoUrl = league.logo ?: ""
        ),
        homeTeam = Team(
            id = teams.home.id,
            name = teams.home.name,
            logoUrl = teams.home.logo ?: ""
        ),
        awayTeam = Team(
            id = teams.away.id,
            name = teams.away.name,
            logoUrl = teams.away.logo ?: ""
        ),
        homeScore = goals.home,
        awayScore = goals.away,
        status = statusEnum,
        minute = fixture.status.elapsed?.toString(),
        date = rawDate,
        time = rawTime,
        stadium = fixture.venue?.name ?: "ملعب غير محدد",
        channel = "beIN Sports / القنوات الرياضية",
        commentator = "يحدد لاحقاً",
        stats = null
    )
}

private fun translateLeagueName(id: Int, defaultName: String): String {
    return when (id) {
        233 -> "الدوري المصري الممتاز"
        2 -> "دوري أبطال أوروبا"
        39 -> "الدوري الإنجليزي الممتاز"
        140 -> "الدوري الإسباني"
        135 -> "الدوري الإيطالي"
        78 -> "الدوري الألماني"
        61 -> "الدوري الفرنسي"
        307 -> "دوري روشن السعودي"
        else -> defaultName
    }
}
