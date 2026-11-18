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
    version = 4,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun movieDao(): CatalogMovieDao

    companion object {
        @Volatile
        private var INSTANCE: CatalogDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_movies_tmdbId ON catalog_movies(tmdbId)")
            }
        }

        fun getInstance(context: Context): CatalogDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CatalogDatabase::class.java,
                    "catalog.db",
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
