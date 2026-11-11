package com.saydlawy.sahab.ui.sports

import com.saydlawy.sahab.data.sports.League
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.Team
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SportsFilterTest {

    private val baseDay = "2026-11-11"

    private fun offsetDate(days: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply { time = sdf.parse(baseDay)!! }
        cal.add(Calendar.DAY_OF_YEAR, days)
        return sdf.format(cal.time)
    }

    private fun matchOn(id: Int, dayOffset: Int, leagueId: Int = 233): Match = Match(
        id = id,
        league = League(leagueId, "الدوري المصري الممتاز", "Egypt"),
        homeTeam = Team(id * 10, "Team A$id"),
        awayTeam = Team(id * 10 + 1, "Team B$id"),
        homeScore = null,
        awayScore = null,
        status = MatchStatus.UPCOMING,
        minute = null,
        date = offsetDate(dayOffset),
        time = "18:00",
        stadium = "Cairo",
        channel = "",
        commentator = "",
    )

    @Test
    fun dateFilters_computeAccurateCountsAndRangesFrom7To90Days() {
        val sample = listOf(
            matchOn(1, -1), // yesterday
            matchOn(2, 0),  // today
            matchOn(3, 1),  // tomorrow
            matchOn(4, 5),  // within 7 days
            matchOn(5, 12), // within 14 days
            matchOn(6, 25), // within 28 days
            matchOn(7, 45), // within 60 days
            matchOn(8, 80), // within 90 days
        )

        val todayRes = SportsViewModel.filterMatchesPure(
            matchesToFilter = sample,
            category = MainCategory.EGYPTIAN,
            subLeague = 233,
            query = "",
            filter = DateFilter.TODAY,
            todayDate = baseDay,
            targetDate = baseDay,
            dateOfOffset = ::offsetDate,
        )
        assertEquals(listOf(2), todayRes.sortedMatches.map { it.id })
        assertEquals(1, todayRes.counts[DateFilter.YESTERDAY])
        assertEquals(1, todayRes.counts[DateFilter.TODAY])
        assertEquals(1, todayRes.counts[DateFilter.TOMORROW])
        assertEquals(3, todayRes.counts[DateFilter.DAYS_7])
        assertEquals(4, todayRes.counts[DateFilter.DAYS_14])
        assertEquals(5, todayRes.counts[DateFilter.DAYS_28])
        assertEquals(6, todayRes.counts[DateFilter.DAYS_60])
        assertEquals(7, todayRes.counts[DateFilter.DAYS_90])

        val days90Res = SportsViewModel.filterMatchesPure(
            matchesToFilter = sample,
            category = MainCategory.EGYPTIAN,
            subLeague = 233,
            query = "",
            filter = DateFilter.DAYS_90,
            todayDate = baseDay,
            targetDate = baseDay,
            dateOfOffset = ::offsetDate,
        )
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8), days90Res.sortedMatches.map { it.id })

        // When a date has zero matches, filter strictly returns empty (no silent fallback to other dates).
        val emptySample = sample.filterNot { it.id == 2 }
        val emptyTodayRes = SportsViewModel.filterMatchesPure(
            matchesToFilter = emptySample,
            category = MainCategory.EGYPTIAN,
            subLeague = 233,
            query = "",
            filter = DateFilter.TODAY,
            todayDate = baseDay,
            targetDate = baseDay,
            dateOfOffset = ::offsetDate,
        )
        assertEquals(emptyList<Int>(), emptyTodayRes.sortedMatches.map { it.id })

        // Text search scans all dates within the selected league/category even if TODAY is selected.
        val searchAcrossDates = SportsViewModel.filterMatchesPure(
            matchesToFilter = sample,
            category = MainCategory.EGYPTIAN,
            subLeague = 233,
            query = "Team A8",
            filter = DateFilter.TODAY,
            todayDate = baseDay,
            targetDate = baseDay,
            dateOfOffset = ::offsetDate,
        )
        assertEquals(listOf(8), searchAcrossDates.sortedMatches.map { it.id })
    }
}
