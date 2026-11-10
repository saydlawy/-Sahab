package com.saydlawy.sahab.data.sports

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: Int,
    val leagueId: Int,
    val leagueName: String,
    val leagueCountry: String,
    val homeTeamId: Int,
    val homeTeamName: String,
    val homeTeamLogo: String,
    val awayTeamId: Int,
    val awayTeamName: String,
    val awayTeamLogo: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: String,
    val minute: String?,
    val date: String,
    val time: String,
    val stadium: String,
    val channel: String,
    val commentator: String,
    val savedAt: Long
)

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches ORDER BY date ASC, time ASC")
    suspend fun getAllMatches(): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE leagueId = :leagueId ORDER BY date ASC, time ASC")
    suspend fun getMatchesByLeague(leagueId: Int): List<MatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(matches: List<MatchEntity>)

    @Query("DELETE FROM matches WHERE savedAt < :timestamp")
    suspend fun deleteOldMatches(timestamp: Long)
    
    @Query("DELETE FROM matches")
    suspend fun clearAll()
}

@Database(entities = [MatchEntity::class], version = 1, exportSchema = false)
abstract class SportsDatabase : RoomDatabase() {
    abstract fun matchDao(): MatchDao
}

// دوال التحويل بين Match و MatchEntity
fun Match.toEntity(): MatchEntity {
    return MatchEntity(
        id = id,
        leagueId = league.id,
        leagueName = league.name,
        leagueCountry = league.country,
        homeTeamId = homeTeam.id,
        homeTeamName = homeTeam.name,
        homeTeamLogo = homeTeam.logoUrl,
        awayTeamId = awayTeam.id,
        awayTeamName = awayTeam.name,
        awayTeamLogo = awayTeam.logoUrl,
        homeScore = homeScore,
        awayScore = awayScore,
        status = status.name,
        minute = minute,
        date = date,
        time = time,
        stadium = stadium,
        channel = channel,
        commentator = commentator,
        savedAt = System.currentTimeMillis()
    )
}

fun MatchEntity.toMatch(): Match {
    return Match(
        id = id,
        league = League(leagueId, leagueName, leagueCountry),
        homeTeam = Team(homeTeamId, homeTeamName, homeTeamLogo),
        awayTeam = Team(awayTeamId, awayTeamName, awayTeamLogo),
        homeScore = homeScore,
        awayScore = awayScore,
        status = try { MatchStatus.valueOf(status) } catch(e: Exception) { MatchStatus.UPCOMING },
        minute = minute,
        date = date,
        time = time,
        stadium = stadium,
        channel = channel,
        commentator = commentator
    )
}
