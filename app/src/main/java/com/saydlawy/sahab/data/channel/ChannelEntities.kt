package com.saydlawy.sahab.data.channel

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Minimal projection for logo lookups — avoids Room Map return type quirks. */
data class LogoRow(val streamId: String, val logoUrl: String)

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["epgId"]),
        Index(value = ["groupId"]),
        Index(value = ["categoryId"]),
        Index(value = ["sortOrder"]),
        Index(value = ["isHidden"]),
        Index(value = ["isFavorite"]),
        Index(value = ["lastFirebaseCheckAt"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey val streamId: String,
    val name: String,
    val displayName: String,
    val logoUrl: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val channelNumber: Int? = null,
    val epgId: String = "",
    val qualityTag: String = "",
    val groupId: String = "",
    val streamUrlTemplate: String,
    val sortOrder: Int = Int.MAX_VALUE,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val lastWatchedAt: Long = 0L,
    val updatedAt: Long,
    /**
     * Unix millis of last Firebase lookup for this channel.
     * 0 = never checked. Used with a 7-day TTL to avoid re-querying channels
     * that don't yet exist in global_channels.
     */
    val lastFirebaseCheckAt: Long = 0L,
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["epgId", "start"]),
        Index(value = ["start", "end"]),
    ],
)
data class EpgProgramEntity(
    @PrimaryKey val id: String,
    val epgId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val description: String = "",
    val category: String? = null,
    val updatedAt: Long,
)

@Entity(
    tableName = "channel_lists",
    indices = [Index(value = ["accountId"])],
)
data class ChannelListEntity(
    @PrimaryKey val listId: String,
    val name: String,
    val icon: String = "",
    val sortOrder: Int = 0,
    val sortMode: String = "custom",
    val createdAt: Long,
    val updatedAt: Long,
    val accountId: String = "",
)

@Entity(
    tableName = "channel_list_items",
    primaryKeys = ["listId", "streamId"],
    indices = [Index(value = ["listId", "position"])],
)
data class ChannelListItemEntity(
    val listId: String,
    val streamId: String,
    val position: Int = 0,
)
