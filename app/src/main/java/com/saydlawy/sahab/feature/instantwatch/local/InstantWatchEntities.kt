package com.saydlawy.sahab.feature.instantwatch.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "instant_watch_history")
data class InstantWatchHistoryEntity(
    @PrimaryKey val contentKey: String,
    val tmdbId: Int,
    val imdbId: String? = null,
    val mediaType: String, // "movie" or "tv"
    val title: String,
    val originalTitle: String? = null,
    val releaseYear: Int? = null,
    val posterUrl: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAtMs: Long = System.currentTimeMillis(),
)
