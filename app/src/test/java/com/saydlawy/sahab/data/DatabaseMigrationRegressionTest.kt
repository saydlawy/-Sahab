package com.saydlawy.sahab.data

import android.content.ContentResolver
import android.content.ContentValues
import android.database.CharArrayBuffer
import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.net.Uri
import android.os.Bundle
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
                return SingleColumnCursor("name", cols.toList())
            }
            if (trimmed.contains("sqlite_master", ignoreCase = true)) {
                val tableName = bindArgs.firstOrNull()?.toString()
                val values = if (tableName != null && tables.containsKey(tableName)) {
                    listOf(tableName)
                } else {
                    emptyList()
                }
                return SingleColumnCursor("name", values)
            }
            return SingleColumnCursor("name", emptyList())
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

    private class SingleColumnCursor(
        private val columnName: String,
        private val values: List<String>,
    ) : Cursor {
        private var pos = -1
        private var closed = false

        override fun getCount(): Int = values.size

        override fun getPosition(): Int = pos

        override fun move(offset: Int): Boolean = moveToPosition(pos + offset)

        override fun moveToPosition(position: Int): Boolean {
            if (position >= values.size) {
                pos = values.size
                return false
            }
            if (position < 0) {
                pos = -1
                return false
            }
            pos = position
            return true
        }

        override fun moveToFirst(): Boolean = moveToPosition(0)

        override fun moveToLast(): Boolean = moveToPosition(values.size - 1)

        override fun moveToNext(): Boolean = moveToPosition(pos + 1)

        override fun moveToPrevious(): Boolean = moveToPosition(pos - 1)

        override fun isFirst(): Boolean = pos == 0 && values.isNotEmpty()

        override fun isLast(): Boolean = pos == values.lastIndex && values.isNotEmpty()

        override fun isBeforeFirst(): Boolean = values.isEmpty() || pos < 0

        override fun isAfterLast(): Boolean = values.isEmpty() || pos >= values.size

        override fun getColumnIndex(columnName: String?): Int =
            if (columnName == this.columnName) 0 else -1

        override fun getColumnIndexOrThrow(columnName: String?): Int {
            val index = getColumnIndex(columnName)
            if (index < 0) throw IllegalArgumentException("Column '$columnName' does not exist.")
            return index
        }

        override fun getColumnName(columnIndex: Int): String {
            if (columnIndex != 0) throw IndexOutOfBoundsException("Invalid column index: $columnIndex")
            return columnName
        }

        override fun getColumnNames(): Array<String> = arrayOf(columnName)

        override fun getColumnCount(): Int = 1

        override fun getBlob(columnIndex: Int): ByteArray = throw UnsupportedOperationException()

        override fun getString(columnIndex: Int): String {
            if (columnIndex != 0) throw IndexOutOfBoundsException("Invalid column index: $columnIndex")
            return values[pos]
        }

        override fun copyStringToBuffer(columnIndex: Int, buffer: CharArrayBuffer?) =
            throw UnsupportedOperationException()

        override fun getShort(columnIndex: Int): Short = throw UnsupportedOperationException()

        override fun getInt(columnIndex: Int): Int = throw UnsupportedOperationException()

        override fun getLong(columnIndex: Int): Long = throw UnsupportedOperationException()

        override fun getFloat(columnIndex: Int): Float = throw UnsupportedOperationException()

        override fun getDouble(columnIndex: Int): Double = throw UnsupportedOperationException()

        override fun getType(columnIndex: Int): Int = Cursor.FIELD_TYPE_STRING

        override fun isNull(columnIndex: Int): Boolean = false

        override fun deactivate() = Unit

        @Deprecated("Deprecated in Java")
        override fun requery(): Boolean = false

        override fun close() {
            closed = true
        }

        override fun isClosed(): Boolean = closed

        override fun registerContentObserver(observer: ContentObserver?) = Unit

        override fun unregisterContentObserver(observer: ContentObserver?) = Unit

        override fun registerDataSetObserver(observer: DataSetObserver?) = Unit

        override fun unregisterDataSetObserver(observer: DataSetObserver?) = Unit

        override fun setNotificationUri(cr: ContentResolver?, uri: Uri?) = Unit

        override fun getNotificationUri(): Uri? = null

        override fun getWantsAllOnMoveCalls(): Boolean = false

        override fun setExtras(extras: Bundle?) = Unit

        override fun getExtras(): Bundle = Bundle()

        override fun respond(extras: Bundle?): Bundle = Bundle()
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

        assertFalse(db.executedSql.any { it.contains("ADD COLUMN `lastFirebaseCheckAt`", ignoreCase = true) })
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
        assertEquals(1700000000L, migratedRow["createdAt"])
        assertEquals(1700000000L, migratedRow["updatedAt"])
    }

    @Test
    fun catalogDatabase_migrations2To4_upgradePreservesRowsAndAddsImdbAndIndices() {
        val db = RecordingSqliteDatabase()
        db.defineTable(
            name = "catalog_movies",
            columns = listOf(
                "sahabId", "novaStreamId", "categoryId", "titleAr", "titleEn", "year",
                "plot", "plotSource", "plotConfidence", "identifiedTitle", "mediaType",
                "cast", "director", "genre", "runtime", "rating", "posterUrl",
                "backdropUrl", "novaAdded", "containerExtension", "isAdult", "tmdbId",
                "tmdbVoteCount", "needsEnrichment", "lastEnriched", "lastSeen", "deleted",
            ),
            rows = listOf(mapOf("sahabId" to "tmdb:603", "novaStreamId" to "9902", "titleEn" to "The Matrix")),
        )
        db.indices["index_catalog_movies_novaStreamId"] = true

        CatalogDatabase.MIGRATION_2_3.migrate(db)
        assertTrue(db.tables["catalog_movies"]!!.contains("imdbId"))

        CatalogDatabase.MIGRATION_3_4.migrate(db)
        assertFalse(db.indices.containsKey("index_catalog_movies_novaStreamId"))
        assertEquals(true, db.indices["index_catalog_movies_mediaType_novaStreamId"])
        assertTrue(db.indices.containsKey("index_catalog_movies_tmdbId"))
        assertEquals(1, db.tableRows["catalog_movies"]!!.size)
        assertEquals("tmdb:603", db.tableRows["catalog_movies"]!![0]["sahabId"])
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

    @Test
    fun catalogDatabase_migration3To4_emitsDeduplicationBeforeUniqueIndexCreation() {
        val db = RecordingSqliteDatabase()
        db.defineTable(
            name = "catalog_movies",
            columns = listOf("sahabId", "novaStreamId", "mediaType", "categoryId", "needsEnrichment", "novaAdded", "tmdbId", "lastSeen", "deleted"),
            rows = listOf(mapOf("sahabId" to "pending:12", "novaStreamId" to "12", "mediaType" to "movie")),
        )
        db.indices["index_catalog_movies_novaStreamId"] = true

        CatalogDatabase.MIGRATION_3_4.migrate(db)

        val deleteIdx = db.executedSql.indexOfFirst {
            it.contains("DELETE FROM `catalog_movies`", ignoreCase = true) &&
                it.contains("GROUP BY `mediaType`, `novaStreamId`", ignoreCase = true)
        }
        val createIdx = db.executedSql.indexOfFirst {
            it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_catalog_movies_mediaType_novaStreamId`", ignoreCase = true)
        }
        assertTrue("MIGRATION_3_4 must deduplicate rows before creating unique index", deleteIdx >= 0)
        assertTrue("MIGRATION_3_4 must create mediaType+novaStreamId unique index", createIdx > deleteIdx)
    }

    @Test
    fun channelDatabase_migration2To3_addsAccountIdAndPreservesCustomLists() {
        val db = RecordingSqliteDatabase()
        db.defineTable(
            name = "channel_lists",
            columns = listOf("listId", "name", "icon", "sortOrder", "sortMode", "createdAt", "updatedAt"),
            rows = listOf(
                mapOf("listId" to "custom_1", "name" to "أخبار", "icon" to "", "sortOrder" to 0, "sortMode" to "custom", "createdAt" to 100L, "updatedAt" to 100L),
            ),
        )

        ChannelDatabase.MIGRATION_2_3.migrate(db)

        assertTrue(db.tables["channel_lists"]!!.contains("accountId"))
        assertTrue(db.indices.containsKey("index_channel_lists_accountId"))
        assertEquals(1, db.tableRows["channel_lists"]!!.size)
        assertEquals("custom_1", db.tableRows["channel_lists"]!![0]["listId"])
        assertEquals("أخبار", db.tableRows["channel_lists"]!![0]["name"])
    }

    @Test
    fun catalogDatabase_migrations6To8_preservesDataAddsProviderOrderEpisodesSearchTitleAndDropsFilterIndices() {
        val db = RecordingSqliteDatabase()
        db.defineTable(
            name = "catalog_movies",
            columns = listOf(
                "sahabId", "novaStreamId", "categoryId", "titleAr", "titleEn", "year",
                "plot", "plotSource", "plotConfidence", "identifiedTitle", "mediaType",
                "cast", "castJson", "director", "genre", "runtime", "rating", "posterUrl",
                "backdropUrl", "postersJson", "trailerUrl", "imdbId", "novaAdded",
                "containerExtension", "isAdult", "tmdbId", "tmdbVoteCount",
                "needsEnrichment", "lastEnriched", "lastSeen", "deleted",
            ),
            rows = listOf(
                mapOf(
                    "sahabId" to "pending:9902",
                    "novaStreamId" to "9902",
                    "mediaType" to "movie",
                    "titleAr" to "ماتريكس",
                    "titleEn" to "The Matrix",
                    "plot" to "قصة محفوظة محلياً",
                    "tmdbId" to 603,
                ),
            ),
        )
        db.indices["index_catalog_movies_mediaType_novaStreamId"] = true
        db.indices["index_catalog_movies_categoryId_novaAdded"] = false
        db.indices["index_catalog_movies_needsEnrichment_novaAdded"] = false
        db.indices["index_catalog_movies_tmdbId"] = false
        db.indices["index_catalog_movies_lastSeen"] = false
        db.indices["index_catalog_movies_deleted"] = false

        CatalogDatabase.MIGRATION_6_7.migrate(db)
        CatalogDatabase.MIGRATION_7_8.migrate(db)

        val cols = db.tables["catalog_movies"]!!
        assertTrue(cols.containsAll(listOf("needsCloudPush", "updatedAt", "providerOrder", "episodesJson", "searchTitle")))

        // Dropped legacy filter/added indices
        assertFalse(db.indices.containsKey("index_catalog_movies_categoryId_novaAdded"))
        assertFalse(db.indices.containsKey("index_catalog_movies_needsEnrichment_novaAdded"))
        assertFalse(db.indices.containsKey("index_catalog_movies_lastSeen"))
        assertFalse(db.indices.containsKey("index_catalog_movies_deleted"))
        assertFalse(db.indices.containsKey("index_catalog_movies_mediaType_deleted_novaAdded"))
        assertFalse(db.indices.containsKey("index_catalog_movies_mediaType_deleted_rating"))
        assertFalse(db.indices.containsKey("index_catalog_movies_mediaType_deleted_year"))

        // Retained + new provider-order indices
        assertEquals(true, db.indices["index_catalog_movies_mediaType_novaStreamId"])
        assertTrue(db.indices.containsKey("index_catalog_movies_tmdbId"))
        assertTrue(db.indices.containsKey("index_catalog_movies_needsCloudPush_updatedAt"))
        assertTrue(db.indices.containsKey("index_catalog_movies_mediaType_deleted_providerOrder_sahabId"))
        assertTrue(db.indices.containsKey("index_catalog_movies_mediaType_deleted_categoryId_providerOrder_sahabId"))
        assertTrue(db.indices.containsKey("index_catalog_movies_mediaType_deleted_needsEnrichment_providerOrder"))
        assertTrue(db.indices.containsKey("index_catalog_movies_mediaType_deleted_lastSeen"))

        // Existing user row & TMDB enrichment preserved intact
        assertEquals(1, db.tableRows["catalog_movies"]!!.size)
        val preserved = db.tableRows["catalog_movies"]!![0]
        assertEquals("pending:9902", preserved["sahabId"])
        assertEquals("9902", preserved["novaStreamId"])
        assertEquals(603, preserved["tmdbId"])
        assertEquals("قصة محفوظة محلياً", preserved["plot"])
    }
}
