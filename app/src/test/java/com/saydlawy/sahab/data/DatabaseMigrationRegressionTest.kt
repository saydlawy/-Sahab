package com.saydlawy.sahab.data

import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.util.Pair
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.saydlawy.sahab.data.catalog.CatalogDatabase
import com.saydlawy.sahab.data.channel.ChannelDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DatabaseMigrationRegressionTest {

    /**
     * Lightweight SQLite schema + data simulator implementing [SupportSQLiteDatabase] so Room
     * migrations (`ChannelDatabase.MIGRATION_1_2` and `CatalogDatabase.MIGRATION_1_2..3_4`) can
     * be executed and verified in pure JVM unit tests without requiring an emulator.
     */
    private class RecordingSqliteDatabase : SupportSQLiteDatabase {
        val tables = linkedMapOf<String, MutableSet<String>>()
        val indices = linkedMapOf<String, Boolean>() // indexName -> unique
        val tableRows = linkedMapOf<String, MutableList<Map<String, Any?>>>()
        val executedSql = mutableListOf<String>()

        fun defineTable(name: String, columns: List<String>, rows: List<Map<String, Any?>> = emptyList()) {
            tables[name] = columns.toMutableSet()
            tableRows[name] = rows.map { it.toMap() }.toMutableList()
        }

        override fun execSQL(sql: String) {
            val normalized = sql.trim().replace(Regex("\\s+"), " ")
            executedSql += normalized

            val addColRegex = Regex(
                "ALTER TABLE `?(\\w+)`? ADD COLUMN `?(\\w+)`?.*",
                RegexOption.IGNORE_CASE,
            )
            val renameTableRegex = Regex(
                "ALTER TABLE `?(\\w+)`? RENAME TO `?(\\w+)`?",
                RegexOption.IGNORE_CASE,
            )
            val createTableRegex = Regex(
                "CREATE TABLE IF NOT EXISTS `?(\\w+)`? \\((.+)\\)",
                RegexOption.IGNORE_CASE,
            )
            val dropTableRegex = Regex(
                "DROP TABLE (?:IF EXISTS )?`?(\\w+)`?",
                RegexOption.IGNORE_CASE,
            )
            val dropIndexRegex = Regex(
                "DROP INDEX IF EXISTS `?(\\w+)`?",
                RegexOption.IGNORE_CASE,
            )
            val createIndexRegex = Regex(
                "CREATE (UNIQUE )?INDEX IF NOT EXISTS `?(\\w+)`? ON `?(\\w+)`? \\((.+)\\)",
                RegexOption.IGNORE_CASE,
            )
            val migrateLegacyListsRegex = Regex(
                "INSERT OR IGNORE INTO `channel_lists` .* FROM `channel_lists_legacy`",
                RegexOption.IGNORE_CASE,
            )

            addColRegex.matchEntire(normalized)?.let { m ->
                val table = m.groupValues[1]
                val col = m.groupValues[2]
                tables.getOrPut(table) { linkedSetOf() }.add(col)
                return
            }

            renameTableRegex.matchEntire(normalized)?.let { m ->
                val oldName = m.groupValues[1]
                val newName = m.groupValues[2]
                tables.remove(oldName)?.let { tables[newName] = it }
                tableRows.remove(oldName)?.let { tableRows[newName] = it }
                return
            }

            createTableRegex.matchEntire(normalized)?.let { m ->
                val table = m.groupValues[1]
                val body = m.groupValues[2]
                if (!tables.containsKey(table)) {
                    val cols = body.split(",")
                        .map { it.trim() }
                        .filterNot { it.uppercase(Locale.US).startsWith("PRIMARY KEY") }
                        .mapNotNull { def ->
                            Regex("^`?(\\w+)`?\\s+").find(def)?.groupValues?.get(1)
                        }
                        .toMutableSet()
                    tables[table] = cols
                    tableRows.putIfAbsent(table, mutableListOf())
                }
                return
            }

            if (migrateLegacyListsRegex.containsMatchIn(normalized)) {
                val legacyRows = tableRows["channel_lists_legacy"].orEmpty()
                val targetRows = tableRows.getOrPut("channel_lists") { mutableListOf() }
                for (row in legacyRows) {
                    targetRows += mapOf(
                        "listId" to row["id"],
                        "name" to row["name"],
                        "icon" to "",
                        "sortOrder" to row["sortOrder"],
                        "sortMode" to "custom",
                        "createdAt" to row["updatedAt"],
                        "updatedAt" to row["updatedAt"],
                    )
                }
                return
            }

            dropTableRegex.matchEntire(normalized)?.let { m ->
                val table = m.groupValues[1]
                tables.remove(table)
                tableRows.remove(table)
                return
            }

            dropIndexRegex.matchEntire(normalized)?.let { m ->
                indices.remove(m.groupValues[1])
                return
            }

            createIndexRegex.matchEntire(normalized)?.let { m ->
                val isUnique = m.groupValues[1].isNotBlank()
                val idxName = m.groupValues[2]
                indices[idxName] = isUnique
                return
            }
        }

        override fun query(query: String): Cursor = query(query, emptyArray())

        override fun query(query: String, bindArgs: Array<out Any?>): Cursor {
            val trimmed = query.trim()
            val pragmaMatch = Regex("PRAGMA table_info\\(`?(\\w+)`?\\)", RegexOption.IGNORE_CASE).find(trimmed)
            if (pragmaMatch != null) {
                val table = pragmaMatch.groupValues[1]
                val cols = tables[table].orEmpty()
                return FakeSingleColumnCursor("name", cols.toList())
            }
            if (trimmed.contains("sqlite_master", ignoreCase = true)) {
                val tableName = bindArgs.firstOrNull()?.toString()
                val values = if (tableName != null && tables.containsKey(tableName)) {
                    listOf(tableName)
                } else {
                    emptyList()
                }
                return FakeSingleColumnCursor("name", values)
            }
            return FakeSingleColumnCursor("name", emptyList())
        }

        // Unused SupportSQLiteDatabase members for migration unit tests
        override fun compileStatement(sql: String): SupportSQLiteStatement = throw UnsupportedOperationException()
        override fun beginTransaction() = Unit
        override fun beginTransactionNonExclusive() = Unit
        override fun beginTransactionWithListener(transactionListener: android.database.sqlite.SQLiteTransactionListener) = Unit
        override fun beginTransactionWithListenerNonExclusive(transactionListener: android.database.sqlite.SQLiteTransactionListener) = Unit
        override fun endTransaction() = Unit
        override fun setTransactionSuccessful() = Unit
        override fun inTransaction(): Boolean = false
        override val isDbLockedByCurrentThread: Boolean = false
        override fun yieldIfContendedSafely(): Boolean = false
        override fun yieldIfContendedSafely(sleepAfterYieldDelayMillis: Long): Boolean = false
        override var version: Int = 1
        override val maximumSize: Long = 0L
        override fun setMaximumSize(numBytes: Long): Long = 0L
        override var pageSize: Long = 4096L
        override fun query(query: SupportSQLiteQuery): Cursor = throw UnsupportedOperationException()
        override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor = throw UnsupportedOperationException()
        override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long = 0L
        override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int = 0
        override fun update(table: String, conflictAlgorithm: Int, values: ContentValues, whereClause: String?, whereArgs: Array<out Any?>?): Int = 0
        override fun execSQL(sql: String, bindArgs: Array<out Any?>) = execSQL(sql)
        override val isReadOnly: Boolean = false
        override val isOpen: Boolean = true
        override fun needUpgrade(newVersion: Int): Boolean = true
        override val path: String = ":memory:"
        override fun setLocale(locale: Locale) = Unit
        override fun setMaxSqlCacheSize(cacheSize: Int) = Unit
        override fun setForeignKeyConstraintsEnabled(enabled: Boolean) = Unit
        override fun enableWriteAheadLogging(): Boolean = true
        override fun disableWriteAheadLogging() = Unit
        override val isWriteAheadLoggingEnabled: Boolean = true
        override val attachedDbs: List<Pair<String, String>>? = emptyList()
        override val isDatabaseIntegrityOk: Boolean = true
        override fun close() = Unit
    }

    private class FakeSingleColumnCursor(
        private val columnName: String,
        private val values: List<String>,
    ) : MatrixCursor(arrayOf(columnName)) {
        private var pos = -1

        override fun getColumnIndex(name: String?): Int =
            if (name == columnName) 0 else -1

        override fun moveToFirst(): Boolean {
            pos = 0
            return values.isNotEmpty()
        }

        override fun moveToNext(): Boolean {
            pos++
            return pos in values.indices
        }

        override fun getString(column: Int): String = values[pos]

        override fun close() = Unit
    }

    @Test
    fun channelDatabase_migration1To2_addsFirebaseColumnAndCanonicalListTablesWithoutDataLoss() {
        val db = RecordingSqliteDatabase()
        db.defineTable(
            name = "channels",
            columns = listOf(
                "streamId", "name", "displayName", "logoUrl", "categoryId", "categoryName",
                "channelNumber", "epgId", "qualityTag", "groupId", "streamUrlTemplate",
                "sortOrder", "isFavorite", "isHidden", "lastWatchedAt", "updatedAt",
            ),
            rows = listOf(mapOf("streamId" to "1001", "name" to "Al Jazeera")),
        )

        ChannelDatabase.MIGRATION_1_2.migrate(db)

        // Verify channels table gained lastFirebaseCheckAt and its index while preserving existing rows
        assertTrue(db.tables["channels"]!!.contains("lastFirebaseCheckAt"))
        assertTrue(db.indices.containsKey("index_channels_lastFirebaseCheckAt"))
        assertEquals(1, db.tableRows["channels"]!!.size)
        assertEquals("1001", db.tableRows["channels"]!![0]["streamId"])

        // Verify channel_lists matches ChannelListEntity schema
        val listCols = db.tables["channel_lists"]!!
        assertEquals(
            setOf("listId", "name", "icon", "sortOrder", "sortMode", "createdAt", "updatedAt"),
            listCols,
        )

        // Verify channel_list_items and its composite index exist
        assertEquals(
            setOf("listId", "streamId", "position"),
            db.tables["channel_list_items"],
        )
        assertTrue(db.indices.containsKey("index_channel_list_items_listId_position"))
    }

    @Test
    fun channelDatabase_migration1To2_preservesLegacyIdBasedCustomLists() {
        val db = RecordingSqliteDatabase()
        db.defineTable(
            name = "channels",
            columns = listOf("streamId", "name", "lastFirebaseCheckAt"),
        )
        db.defineTable(
            name = "channel_lists",
            columns = listOf("id", "name", "sortOrder", "updatedAt"),
            rows = listOf(
                mapOf("id" to "list_sports", "name" to "قنواتي", "sortOrder" to 1, "updatedAt" to 1700000000L),
            ),
        )

        ChannelDatabase.MIGRATION_1_2.migrate(db)

        assertFalse(db.tables.containsKey("channel_lists_legacy"))
        assertEquals(
            setOf("listId", "name", "icon", "sortOrder", "sortMode", "createdAt", "updatedAt"),
            db.tables["channel_lists"],
        )
        assertEquals(1, db.tableRows["channel_lists"]!!.size)
        val migratedRow = db.tableRows["channel_lists"]!![0]
        assertEquals("list_sports", migratedRow["listId"])
        assertEquals("قنواتي", migratedRow["name"])
        assertEquals(1, migratedRow["sortOrder"])
        assertEquals("custom", migratedRow["sortMode"])
    }

    @Test
    fun catalogDatabase_migrations1To4_upgradeFromV1AndV2PreservingRowsAndUpdatingIndices() {
        val db = RecordingSqliteDatabase()
        db.defineTable(
            name = "catalog_movies",
            columns = listOf(
                "sahabId", "novaStreamId", "categoryId", "titleAr", "titleEn", "year",
                "plot", "cast", "director", "genre", "runtime", "rating", "posterUrl",
                "backdropUrl", "novaAdded", "containerExtension", "isAdult", "tmdbId",
                "tmdbVoteCount", "needsEnrichment", "lastEnriched", "lastSeen", "deleted",
            ),
            rows = listOf(mapOf("sahabId" to "tmdb:550", "novaStreamId" to "9901", "titleEn" to "Fight Club")),
        )
        db.indices["index_catalog_movies_novaStreamId"] = true

        CatalogDatabase.MIGRATION_1_2.migrate(db)
        val colsAfterV2 = db.tables["catalog_movies"]!!
        assertTrue(colsAfterV2.containsAll(listOf("plotSource", "plotConfidence", "identifiedTitle", "mediaType")))

        CatalogDatabase.MIGRATION_2_3.migrate(db)
        val colsAfterV3 = db.tables["catalog_movies"]!!
        assertTrue(colsAfterV3.contains("imdbId"))

        CatalogDatabase.MIGRATION_3_4.migrate(db)
        assertFalse(db.indices.containsKey("index_catalog_movies_novaStreamId"))
        assertEquals(true, db.indices["index_catalog_movies_mediaType_novaStreamId"])
        assertTrue(db.indices.containsKey("index_catalog_movies_categoryId_novaAdded"))
        assertTrue(db.indices.containsKey("index_catalog_movies_needsEnrichment_novaAdded"))
        assertTrue(db.indices.containsKey("index_catalog_movies_tmdbId"))
        assertTrue(db.indices.containsKey("index_catalog_movies_lastSeen"))
        assertTrue(db.indices.containsKey("index_catalog_movies_deleted"))

        // Existing catalog row preserved intact across v1 -> v2 -> v3 -> v4
        assertEquals(1, db.tableRows["catalog_movies"]!!.size)
        assertEquals("tmdb:550", db.tableRows["catalog_movies"]!![0]["sahabId"])
    }
}
