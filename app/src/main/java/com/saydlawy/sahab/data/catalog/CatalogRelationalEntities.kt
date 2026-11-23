package com.saydlawy.sahab.data.catalog

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalog_categories",
    primaryKeys = ["mediaType", "categoryId"],
    indices = [Index(value = ["mediaType", "sortOrder"])],
)
data class CatalogCategoryEntity(
    val mediaType: String,
    val categoryId: String,
    val name: String,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "series_seasons",
    primaryKeys = ["seriesStreamId", "seasonNumber"],
    indices = [Index(value = ["seriesStreamId"])],
)
data class SeriesSeasonEntity(
    val seriesStreamId: String,
    val seasonNumber: Int,
    val name: String = "",
    val overview: String = "",
    val posterUrl: String = "",
    val airDate: String = "",
    val tmdbSeasonId: Int? = null,
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "series_episodes",
    indices = [
        Index(value = ["seriesStreamId", "seasonNumber", "episodeNumber"]),
        Index(value = ["seriesStreamId"]),
    ],
)
data class SeriesEpisodeEntity(
    @PrimaryKey val episodeId: String,
    val seriesStreamId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String = "",
    val ext: String = "",
    val streamUrl: String = "",
    val iptvImage: String = "",
    val tmdbStillUrl: String = "",
    val plot: String = "",
    val duration: String = "",
    val airDate: String = "",
    val updatedAt: Long = 0L,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val syncKey: String,
    val lastSuccessAt: Long = 0L,
    val remoteVersion: String = "",
    val itemCount: Int = 0,
    val status: String = "idle",
    val lastCursor: String = "",
    val updatedAt: Long = 0L,
)
