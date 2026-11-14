package com.saydlawy.sahab.data.catalog

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseCatalogService {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("global_catalog")

    suspend fun getCatalogItem(sahabId: String): CatalogMovieEntity? = suspendCancellableCoroutine { cont ->
        collection.document(sahabId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    cont.resume(mapToEntity(sahabId, snapshot.data ?: emptyMap()))
                } else {
                    cont.resume(null)
                }
            }
            .addOnFailureListener {
                cont.resume(null) // في حال فشل الاتصال نرجع null لاستخدام الكاش المحلي
            }
    }


    suspend fun getTrendingMovies(limit: Long = 10): List<CatalogMovieEntity> = suspendCancellableCoroutine { cont ->
        collection
            .orderBy("rating", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { doc ->
                    try { mapToEntity(doc.id, doc.data ?: emptyMap()) } catch (e: Exception) { null }
                }
                cont.resume(list)
            }
            .addOnFailureListener {
                cont.resume(emptyList())
            }
    }

    suspend fun saveCatalogItem(entity: CatalogMovieEntity) = suspendCancellableCoroutine { cont ->
        val data = mapOf(
            "titleAr" to entity.titleAr,
            "titleEn" to entity.titleEn,
            "year" to entity.year,
            "plot" to entity.plot,
            "plotSource" to entity.plotSource,
            "plotConfidence" to entity.plotConfidence,
            "identifiedTitle" to entity.identifiedTitle,
            "mediaType" to entity.mediaType,
            "cast" to entity.cast,
            "director" to entity.director,
            "genre" to entity.genre,
            "runtime" to entity.runtime,
            "rating" to entity.rating,
            "posterUrl" to entity.posterUrl,
            "backdropUrl" to entity.backdropUrl,
            "tmdbId" to entity.tmdbId,
            "imdbId" to entity.imdbId,
            "tmdbVoteCount" to entity.tmdbVoteCount
        )
        collection.document(entity.sahabId).set(data)
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) }
    }

    private fun mapToEntity(id: String, data: Map<String, Any>): CatalogMovieEntity {
        return CatalogMovieEntity(
            sahabId = id,
            novaStreamId = "",
            titleAr = data["titleAr"] as? String ?: "",
            titleEn = data["titleEn"] as? String ?: "",
            year = data["year"] as? String ?: "",
            plot = data["plot"] as? String ?: "",
            plotSource = data["plotSource"] as? String ?: "",
            plotConfidence = data["plotConfidence"] as? String ?: "",
            identifiedTitle = data["identifiedTitle"] as? String ?: "",
            mediaType = data["mediaType"] as? String ?: "movie",
            cast = data["cast"] as? String ?: "",
            director = data["director"] as? String ?: "",
            genre = data["genre"] as? String ?: "",
            runtime = data["runtime"] as? String ?: "",
            rating = data["rating"] as? String ?: "",
            posterUrl = data["posterUrl"] as? String ?: "",
            backdropUrl = data["backdropUrl"] as? String ?: "",
            imdbId = data["imdbId"] as? String ?: "",
            tmdbId = (data["tmdbId"] as? Number)?.toInt(),
            tmdbVoteCount = (data["tmdbVoteCount"] as? Number)?.toInt(),
            needsEnrichment = 0,
            lastEnriched = System.currentTimeMillis()
        )
    }
}
