package com.saydlawy.sahab.data.catalog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * قاعدة بيانات كتالوج الأفلام المحلية.
 * تستخدم هجرات صريحة للحفاظ على البيانات المحلية دون مسحها عند الترقية.
 */
@Database(
    entities = [CatalogMovieEntity::class],
    version = 7,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun movieDao(): CatalogMovieDao

    companion object {
        @Volatile
        private var INSTANCE: CatalogDatabase? = null

        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex < 0) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "catalog_movies", "plotSource")) {
                    db.execSQL("ALTER TABLE `catalog_movies` ADD COLUMN `plotSource` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "catalog_movies", "plotConfidence")) {
                    db.execSQL("ALTER TABLE `catalog_movies` ADD COLUMN `plotConfidence` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "catalog_movies", "identifiedTitle")) {
                    db.execSQL("ALTER TABLE `catalog_movies` ADD COLUMN `identifiedTitle` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "catalog_movies", "mediaType")) {
                    db.execSQL("ALTER TABLE `catalog_movies` ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT 'movie'")
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "catalog_movies", "imdbId")) {
                    db.execSQL("ALTER TABLE `catalog_movies` ADD COLUMN `imdbId` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_catalog_movies_novaStreamId`")
                db.execSQL(
                    "DELETE FROM `catalog_movies` WHERE rowid NOT IN (" +
                        "SELECT MAX(rowid) FROM `catalog_movies` GROUP BY `mediaType`, `novaStreamId`" +
                        ")",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_catalog_movies_mediaType_novaStreamId` ON `catalog_movies` (`mediaType`, `novaStreamId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_catalog_movies_categoryId_novaAdded` ON `catalog_movies` (`categoryId`, `novaAdded`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_catalog_movies_needsEnrichment_novaAdded` ON `catalog_movies` (`needsEnrichment`, `novaAdded`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_catalog_movies_tmdbId` ON `catalog_movies` (`tmdbId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_catalog_movies_lastSeen` ON `catalog_movies` (`lastSeen`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_catalog_movies_deleted` ON `catalog_movies` (`deleted`)",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "catalog_movies", "postersJson")) {
                    db.execSQL("ALTER TABLE `catalog_movies` ADD COLUMN `postersJson` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "catalog_movies", "trailerUrl")) {
                    db.execSQL("ALTER TABLE `catalog_movies` ADD COLUMN `trailerUrl` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "catalog_movies", "castJson")) {
                    db.execSQL("ALTER TABLE `catalog_movies` ADD COLUMN `castJson` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "catalog_movies", "needsCloudPush")) {
                    db.execSQL("ALTER TABLE `catalog_movies` ADD COLUMN `needsCloudPush` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "catalog_movies", "updatedAt")) {
                    db.execSQL("ALTER TABLE `catalog_movies` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_catalog_movies_needsCloudPush_updatedAt` ON `catalog_movies` (`needsCloudPush`, `updatedAt`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_catalog_movies_mediaType_deleted_novaAdded` ON `catalog_movies` (`mediaType`, `deleted`, `novaAdded`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_catalog_movies_mediaType_deleted_rating` ON `catalog_movies` (`mediaType`, `deleted`, `rating`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_catalog_movies_mediaType_deleted_year` ON `catalog_movies` (`mediaType`, `deleted`, `year`)",
                )
            }
        }

        fun getInstance(context: Context): CatalogDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CatalogDatabase::class.java,
                    "catalog.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
