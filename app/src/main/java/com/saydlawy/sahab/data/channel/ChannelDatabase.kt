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
    version = 3,
    exportSchema = true,
)
abstract class ChannelDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile
        private var INSTANCE: ChannelDatabase? = null

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

        private fun hasTable(db: SupportSQLiteDatabase, table: String): Boolean {
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(table),
            ).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (hasTable(db, "channels") && !hasColumn(db, "channels", "lastFirebaseCheckAt")) {
                    db.execSQL(
                        "ALTER TABLE `channels` ADD COLUMN `lastFirebaseCheckAt` INTEGER NOT NULL DEFAULT 0",
                    )
                }
                if (hasTable(db, "channels")) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_channels_lastFirebaseCheckAt` ON `channels` (`lastFirebaseCheckAt`)",
                    )
                }

                if (hasTable(db, "channel_lists") && !hasColumn(db, "channel_lists", "listId")) {
                    db.execSQL("ALTER TABLE `channel_lists` RENAME TO `channel_lists_legacy`")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `channel_lists` (`listId` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `sortMode` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`listId`))",
                    )
                    if (hasColumn(db, "channel_lists_legacy", "id")) {
                        db.execSQL(
                            "INSERT OR IGNORE INTO `channel_lists` (`listId`, `name`, `icon`, `sortOrder`, `sortMode`, `createdAt`, `updatedAt`) SELECT `id`, `name`, '', `sortOrder`, 'custom', `updatedAt`, `updatedAt` FROM `channel_lists_legacy`",
                        )
                    }
                    db.execSQL("DROP TABLE `channel_lists_legacy`")
                } else {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `channel_lists` (`listId` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `sortMode` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`listId`))",
                    )
                }

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `channel_list_items` (`listId` TEXT NOT NULL, `streamId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`listId`, `streamId`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_channel_list_items_listId_position` ON `channel_list_items` (`listId`, `position`)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (hasTable(db, "channel_lists") && !hasColumn(db, "channel_lists", "accountId")) {
                    db.execSQL(
                        "ALTER TABLE `channel_lists` ADD COLUMN `accountId` TEXT NOT NULL DEFAULT ''",
                    )
                }
                if (hasTable(db, "channel_lists")) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_channel_lists_accountId` ON `channel_lists` (`accountId`)",
                    )
                }
            }
        }

        fun getInstance(context: Context): ChannelDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChannelDatabase::class.java,
                    "channel.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
