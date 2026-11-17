package com.saydlawy.sahab.data.channel

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * قاعدة بيانات القنوات المباشرة ودليل البرامج (EPG) المحلية.
 * ملاحظة: أي رفع لرقم [version] يتطلب إما إضافة Migration صريحة أو الاعتماد المقصود
 * على إعادة البناء التدميري (لأن كافة بيانات القنوات وEPG قابلة لإعادة المزامنة).
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
