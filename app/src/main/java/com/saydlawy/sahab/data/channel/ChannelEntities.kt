package com.saydlawy.sahab.data.channel

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local channel database — offline mirror of the user's Xtream subscription,
 * enriched with Firestore metadata (displayName, logo, epgId, quality, groupId).
 *
 * streamUrlTemplate: relative path with placeholders {USER}, {PASS}, {ext}.
 *   e.g. "/live/{USER}/{PASS}/2224.{ext}"
 *   Real URL is built at playback time from Prefs (never stored in DB for security).
 */
@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["epgId"]),
        Index(value = ["groupId"]),
        Index(value = ["categoryId"]),
        Index(value = ["sortOrder"]),
        Index(value = ["isHidden"]),
        Index(value = ["isFavorite"]),
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
)

/**
 * EPG programmes for all channels with epgId.
 * id = "{epgId}_{start}" (globally unique).
 * Index (start, end) powers "what's on now" queries.
 */
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

/**
 * User-defined channel lists (e.g. "My Sports", "Kids").
 * Empty in MVP; populated when the customization screen ships.
 */
@Entity(tableName = "channel_lists")
data class ChannelListEntity(
    @PrimaryKey val listId: String,
    val name: String,
    val icon: String = "",
    val sortOrder: Int = 0,
    val sortMode: String = "custom",
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Membership of channels in lists. One channel can belong to multiple lists.
 * position = order within the list.
 */
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
