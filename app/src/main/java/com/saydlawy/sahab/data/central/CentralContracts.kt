package com.saydlawy.sahab.data.central

import com.saydlawy.sahab.data.PlayItem

/** Abstraction boundary for resolving canonical catalog entries from local Room or Sahab Central. */
interface CentralCatalogProvider {
    suspend fun getByContentId(id: CentralContentId): CentralCatalogItem?
    suspend fun searchPaged(query: String, mediaType: String, limit: Int, offset: Int): List<CentralCatalogItem>
    suspend fun countItems(mediaType: String): Int
}

/** Abstraction boundary for identity, ACL capability, and zero-cost platform feature flags. */
interface CentralIdentityProvider {
    suspend fun currentAccountId(): String
    val isSignedInCloud: Boolean
    val isCatalogAdmin: Boolean
    fun featureFlags(): CentralFeatureFlags
}

/** Abstraction boundary for account delta sync state and zero-cost stream grant resolution. */
interface CentralSyncBridge {
    suspend fun buildEnvelope(): CentralSyncEnvelope
    suspend fun resolvePlaybackGrant(item: PlayItem, resumeMs: Long): CentralPlaybackGrant
}

/** Zero-cost telemetry sink contract (must not incur paid network or analytics billing). */
interface CentralTelemetrySink {
    fun recordEvent(name: String, attributes: Map<String, String> = emptyMap())
    fun totalEstimatedCostUsd(): Double = 0.0
}
