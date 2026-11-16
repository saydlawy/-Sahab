package com.saydlawy.sahab.data.sports

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.FileOutputStream

@Entity(
    tableName = "matches",
    indices = [
        Index(value = ["leagueId"]),
        Index(value = ["date"]),
    ],
)
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

@Entity(tableName = "standings", primaryKeys = ["leagueId", "rank"])
data class StandingEntity(
    val leagueId: Int,
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

    @Query("SELECT * FROM standings WHERE leagueId = :leagueId ORDER BY rank ASC")
    suspend fun getStandingsByLeague(leagueId: Int): List<StandingEntity>

    @Query("DELETE FROM standings WHERE leagueId = :leagueId")
    suspend fun deleteStandingsByLeague(leagueId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStandings(standings: List<StandingEntity>)
}

@Database(entities = [MatchEntity::class, StandingEntity::class], version = 2, exportSchema = false)
abstract class SportsDatabase : RoomDatabase() {
    abstract fun matchDao(): MatchDao

    companion object {
        @Volatile
        private var INSTANCE: SportsDatabase? = null

        fun getInstance(context: Context): SportsDatabase {
            INSTANCE?.let { return it }
            return synchronized(this) {
                INSTANCE?.let { return it }
                val app = context.applicationContext
                val dbFile = app.getDatabasePath("sports_db")
                val seedPrefs = app.getSharedPreferences("sahab_sports_seed_prefs", Context.MODE_PRIVATE)
                if (needsSeedCopy(dbFile, seedPrefs)) {
                    try {
                        dbFile.parentFile?.mkdirs()
                        File(dbFile.path + "-wal").delete()
                        File(dbFile.path + "-shm").delete()
                        app.assets.open("databases/sports_db").use { input ->
                            FileOutputStream(dbFile).use { output ->
                                input.copyTo(output)
                                output.flush()
                            }
                        }
                        seedPrefs.edit().putInt(KEY_SEED_VERSION, CURRENT_SEED_VERSION).apply()
                    } catch (e: Exception) {
                        android.util.Log.e("SportsDB", "Failed to copy pre-packaged sports_db from assets", e)
                        throw RuntimeException("SportsDB seed copy failed: ${e.message}", e)
                    }
                }

                val copiedSize = dbFile.length()
                if (copiedSize < 100_000) {
                    throw RuntimeException("SportsDB seed copy produced only ${copiedSize} bytes (expected ~1.2MB)")
                }

                Room.databaseBuilder(app, SportsDatabase::class.java, "sports_db")
                    .addMigrations(SportsMigrations.MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private const val KEY_SEED_VERSION = "seed_version"
        private const val CURRENT_SEED_VERSION = 1

        private fun needsSeedCopy(dbFile: File, seedPrefs: android.content.SharedPreferences): Boolean {
            if (!dbFile.exists()) return true
            if (seedPrefs.getInt(KEY_SEED_VERSION, 0) < CURRENT_SEED_VERSION) return true
            return try {
                SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { sqLite ->
                    isSchemaOutdated(sqLite)
                }
            } catch (e: Exception) {
                true
            }
        }

        private fun isSchemaOutdated(sqLite: SQLiteDatabase): Boolean {
            var leagueIdNotNull = false
            sqLite.rawQuery("PRAGMA table_info(matches)", null).use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                val notNullIdx = cursor.getColumnIndex("notnull")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0 && notNullIdx >= 0 && cursor.getString(nameIdx) == "leagueId") {
                        leagueIdNotNull = cursor.getInt(notNullIdx) == 1
                    }
                }
            }
            var standingsPkValid = false
            sqLite.rawQuery("PRAGMA table_info(standings)", null).use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                val pkIdx = cursor.getColumnIndex("pk")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0 && pkIdx >= 0 && cursor.getString(nameIdx) == "leagueId") {
                        standingsPkValid = cursor.getInt(pkIdx) > 0
                    }
                }
            }
            return !leagueIdNotNull || !standingsPkValid
        }


    }
}

object SportsMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_matches_leagueId` ON `matches` (`leagueId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_matches_date` ON `matches` (`date`)")
        }
    }
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
        league = League(leagueId, AllowedLeaguesConfig.translateLeagueName(leagueId, leagueName), leagueCountry),
        homeTeam = Team(homeTeamId, homeTeamName, homeTeamLogo),
        awayTeam = Team(awayTeamId, awayTeamName, awayTeamLogo),
        homeScore = homeScore,
        awayScore = awayScore,
        status = try { MatchStatus.valueOf(status) } catch (e: Exception) { MatchStatus.UPCOMING },
        minute = minute,
        date = date,
        time = time,
        stadium = stadium,
        channel = channel,
        commentator = commentator
    )
}

fun Standing.toEntity(leagueId: Int): StandingEntity {
    return StandingEntity(
        leagueId = leagueId,
        rank = rank,
        teamName = teamName,
        played = played,
        win = win,
        draw = draw,
        loss = loss,
        goalsFor = goalsFor,
        goalsAgainst = goalsAgainst,
        goalDifference = goalDifference,
        points = points
    )
}

fun StandingEntity.toStanding(): Standing {
    return Standing(
        rank = rank,
        teamName = teamName,
        played = played,
        win = win,
        draw = draw,
        loss = loss,
        goalsFor = goalsFor,
        goalsAgainst = goalsAgainst,
        goalDifference = goalDifference,
        points = points
    )
}

