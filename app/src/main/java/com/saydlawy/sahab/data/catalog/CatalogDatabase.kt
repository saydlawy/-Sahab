package com.saydlawy.sahab.data.catalog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * قاعدة بيانات كتالوج الأفلام المحلية.
 * ملاحظة: أي رفع لرقم [version] يتطلب إما إضافة Migration صريحة أو الاعتماد المقصود
 * على إعادة البناء التدميري (لأن كافة بيانات الكتالوج قابلة لإعادة المزامنة من السحابة والسيرفر).
 */
@Database(
    entities = [CatalogMovieEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun movieDao(): CatalogMovieDao

    companion object {
        @Volatile
        private var INSTANCE: CatalogDatabase? = null

        fun getInstance(context: Context): CatalogDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CatalogDatabase::class.java,
                    "catalog.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
