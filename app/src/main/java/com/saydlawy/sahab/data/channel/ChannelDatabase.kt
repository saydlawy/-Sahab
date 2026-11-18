package com.saydlawy.sahab.data.channel

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * قاعدة بيانات القنوات المباشرة ودليل البرامج (EPG) وقوائم المستخدم المخصصة.
 * تستخدم هجرات صريحة لحماية القوائم المخصصة وإخفاء وترتيب القنوات من الضياع.
 */
@Database(
    entities = [
        ChannelEntity::class,
        EpgProgramEntity::class,
        ChannelListEntity::class,
        ChannelListItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ChannelDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile
        private var INSTANCE: ChannelDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `channel_lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `channel_list_items` (`listId` TEXT NOT NULL, `streamId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`listId`, `streamId`))",
                )
            }
        }

        fun getInstance(context: Context): ChannelDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChannelDatabase::class.java,
                    "channel.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
