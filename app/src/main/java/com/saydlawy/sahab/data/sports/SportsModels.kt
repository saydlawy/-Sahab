package com.saydlawy.sahab.data.sports

data class League(
    val id: Int,
    val name: String,
    val country: String,
    val logoUrl: String = ""
)

data class Team(
    val id: Int,
    val name: String,
    val logoUrl: String = ""
)

enum class MatchStatus {
    LIVE, UPCOMING, FINISHED, POSTPONED, CANCELLED
}

data class MatchStats(
    val homePossession: Int,
    val awayPossession: Int,
    val homeShots: Int,
    val awayShots: Int,
    val homeShotsOnTarget: Int,
    val awayShotsOnTarget: Int,
    val homeCorners: Int,
    val awayCorners: Int
)

data class Match(
    val id: Int,
    val league: League,
    val homeTeam: Team,
    val awayTeam: Team,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: MatchStatus,
    val minute: String?,
    val date: String,
    val time: String,
    val stadium: String,
    val channel: String,
    val commentator: String,
    val homeEvents: List<String> = emptyList(),
    val awayEvents: List<String> = emptyList(),
    val stats: MatchStats? = null
)
