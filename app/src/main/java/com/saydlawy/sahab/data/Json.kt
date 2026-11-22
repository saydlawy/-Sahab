package com.saydlawy.sahab.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val ApiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

internal fun JsonElement?.str(): String = when (this) {
    null -> ""
    JsonNull -> ""
    is JsonPrimitive -> content
    else -> ""
}

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray

internal fun JsonObject.s(key: String): String = this[key].str().trim()

internal fun JsonObject.i(key: String): Int = this[key].str().toDoubleOrNull()?.toInt() ?: 0

internal fun JsonElement?.firstString(): String = when (this) {
    is JsonArray -> this.firstOrNull().str().trim()
    else -> this.str().trim()
}
