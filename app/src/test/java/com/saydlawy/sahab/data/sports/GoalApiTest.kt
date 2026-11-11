package com.saydlawy.sahab.data.sports

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoalApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: GoalApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = GoalApi(
            client = OkHttpClient.Builder().build(),
            apiKey = "test-secret-token",
            baseUrl = server.url("/v1").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fixturesByLeague_parsesFixturesAndAuthorizationHeader() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "success": true,
                  "data": [
                    {
                      "id": "fix_1",
                      "matchStatus": "UPCOMING",
                      "matchLive": false,
                      "kickoffUtc": "2026-11-12T18:30:00.000Z",
                      "homeTeamName": "Al Ahly",
                      "awayTeamName": "Pyramids",
                      "homeTeamScore": null,
                      "awayTeamScore": null
                    },
                    {
                      "id": "fix_2",
                      "matchStatus": "SECOND_HALF",
                      "matchLive": true,
                      "matchElapsed": "68",
                      "kickoffUtc": "2026-11-11T16:00:00.000Z",
                      "homeTeamName": "Zamalek",
                      "awayTeamName": "Al Masry",
                      "homeTeamScore": 2,
                      "awayTeamScore": 1
                    }
                  ],
                  "pagination": { "hasMore": false }
                }
                """.trimIndent(),
            ),
        )

        val egypt = GoalLeagueMapping.findByLeagueId(233)!!
        val res = api.fixturesByLeague(egypt)
        assertTrue(res is ApiResult.Success)
        val matches = (res as ApiResult.Success).data
        assertEquals(2, matches.size)
        assertEquals("2026-11-12", matches[0].date)
        assertEquals("18:30", matches[0].time)
        assertEquals("UPCOMING", matches[0].status)
        assertEquals("LIVE", matches[1].status)
        assertEquals(2, matches[1].homeScore)
        assertEquals(1, matches[1].awayScore)

        val recorded = server.takeRequest()
        assertEquals("Bearer test-secret-token", recorded.getHeader("Authorization"))
        assertTrue(recorded.path!!.contains("/leagues/${egypt.nanoid}/fixtures"))
    }

    @Test
    fun standingsByLeague_parsesTableRows() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "success": true,
                  "data": {
                    "season": "2026/2027",
                    "standings": [
                      {
                        "position": 1,
                        "team": { "name": "Pyramids" },
                        "played": 10,
                        "won": 7,
                        "drawn": 3,
                        "lost": 0,
                        "goalsFor": 20,
                        "goalsAgainst": 6,
                        "goalDifference": 14,
                        "points": 24
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val egypt = GoalLeagueMapping.findByLeagueId(233)!!
        val res = api.standingsByLeague(egypt)
        assertTrue(res is ApiResult.Success)
        val rows = (res as ApiResult.Success).data
        assertEquals(1, rows.size)
        assertEquals("Pyramids", rows[0].teamName)
        assertEquals(24, rows[0].points)
        assertEquals(1, rows[0].rank)
    }
}
