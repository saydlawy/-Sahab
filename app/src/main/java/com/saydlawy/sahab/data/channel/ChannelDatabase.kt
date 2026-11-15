package com.saydlawy.sahab.data.channel

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChannelEntity::class,
        EpgProgramEntity::class,
        ChannelListEntity::class,
        ChannelListItemEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ChannelDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile
        private var INSTANCE: ChannelDatabase? = null

        fun getInstance(context: Context): ChannelDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChannelDatabase::class.java,
                    "channel.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
