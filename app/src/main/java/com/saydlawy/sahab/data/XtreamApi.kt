package com.saydlawy.sahab.data

import com.saydlawy.sahab.util.Ar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeToSequence
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** An error whose message is already written in Arabic and safe to show to the user. */
class ApiException(message: String) : Exception(message)

class XtreamApi(private val client: OkHttpClient) {

    suspend fun request(
        account: Account,
        action: String?,
        params: Map<String, String> = emptyMap(),
    ): JsonElement {
        val url = buildUrl(account, action, params)
        val text = client.newCall(Request.Builder().url(url).build()).awaitBody()
        return withContext(Dispatchers.Default) {
            try {
                ApiJson.parseToJsonElement(text)
            } catch (e: Exception) {
                throw ApiException(Ar.errBadResponse)
            }
        }
    }

    /**
     * Streams huge Xtream array responses (channels / VOD / series) directly from OkHttp's
     * response body without buffering a multi-megabyte String + JsonArray DOM in heap.
     * Also tolerates UTF-8 BOM prefixes and PHP associative-object payloads (`{"0":{...},"5":{...}}`).
     */
    suspend fun <T> streamArray(
        account: Account,
        action: String?,
        params: Map<String, String> = emptyMap(),
        onEmptyOrNonArray: () -> T,
        parseStream: (InputStream) -> T,
    ): T {
        val url = buildUrl(account, action, params)
        return client.newCall(Request.Builder().url(url).build()).awaitStream { rawStream ->
            val pb = PushbackInputStream(BufferedInputStream(rawStream, 64 * 1024), 8)
            var next = pb.read()
            // Skip UTF-8 BOM (0xEF 0xBB 0xBF) if emitted by PHP panels
            if (next == 0xEF) {
                val b2 = pb.read()
                val b3 = pb.read()
                if (b2 == 0xBB && b3 == 0xBF) {
                    next = pb.read()
                } else {
                    if (b3 != -1) pb.unread(b3)
                    if (b2 != -1) pb.unread(b2)
                }
            }
            while (next != -1 && next <= ' '.code) {
                next = pb.read()
            }
            if (next == -1) {
                onEmptyOrNonArray()
            } else if (next == '{'.code) {
                pb.unread(next)
                try {
                    val bodyText = pb.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val root = ApiJson.parseToJsonElement(bodyText) as? kotlinx.serialization.json.JsonObject
                    val extractedArray = root?.let { obj ->
                        val objectEntries = obj.values.filterIsInstance<kotlinx.serialization.json.JsonObject>()
                        when {
                            objectEntries.isNotEmpty() -> kotlinx.serialization.json.JsonArray(objectEntries)
                            else -> obj.values.firstOrNull { it is kotlinx.serialization.json.JsonArray } as? kotlinx.serialization.json.JsonArray
                        }
                    }
                    if (extractedArray == null || extractedArray.isEmpty()) {
                        onEmptyOrNonArray()
                    } else {
                        val arrayBytes = ApiJson.encodeToString(kotlinx.serialization.json.JsonArray.serializer(), extractedArray)
                            .toByteArray(Charsets.UTF_8)
                        parseStream(arrayBytes.inputStream())
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    throw e
                } catch (e: ApiException) {
                    throw e
                } catch (_: Exception) {
                    onEmptyOrNonArray()
                }
            } else if (next != '['.code) {
                onEmptyOrNonArray()
            } else {
                pb.unread(next)
                try {
                    parseStream(pb)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    throw e
                } catch (e: ApiException) {
                    throw e
                } catch (e: Exception) {
                    throw ApiException(Ar.errBadResponse)
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal inline fun <reified D> decodeArraySequence(stream: InputStream): Sequence<D> =
        ApiJson.decodeToSequence(stream, DecodeSequenceMode.ARRAY_WRAPPED)

    private fun buildUrl(
        account: Account,
        action: String?,
        params: Map<String, String>,
    ): okhttp3.HttpUrl {
        val base = try {
            "${account.server}/player_api.php".toHttpUrl()
        } catch (e: IllegalArgumentException) {
            throw ApiException(Ar.errBadUrl)
        }
        return base.newBuilder().apply {
            addQueryParameter("username", account.user)
            addQueryParameter("password", account.pass)
            if (action != null) addQueryParameter("action", action)
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
    }

    private suspend fun <T> Call.awaitStream(reader: (InputStream) -> T): T =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { runCatching { cancel() } }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(mapIo(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        if (!r.isSuccessful) {
                            cont.resumeWithException(ApiException(Ar.errServer(r.code)))
                            return
                        }
                        try {
                            val stream = r.body?.byteStream()
                            val value = if (stream != null) reader(stream) else reader("".byteInputStream())
                            cont.resume(value)
                        } catch (e: ApiException) {
                            cont.resumeWithException(e)
                        } catch (e: IOException) {
                            cont.resumeWithException(mapIo(e))
                        } catch (e: Exception) {
                            cont.resumeWithException(ApiException(Ar.errBadResponse))
                        }
                    }
                }
            })
        }

    private suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(mapIo(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    if (!r.isSuccessful) {
                        cont.resumeWithException(ApiException(Ar.errServer(r.code)))
                        return
                    }
                    try {
                        cont.resume(r.body?.string().orEmpty())
                    } catch (e: IOException) {
                        cont.resumeWithException(mapIo(e))
                    }
                }
            }
        })
    }

    private fun mapIo(e: IOException): ApiException = when (e) {
        is UnknownHostException, is ConnectException -> ApiException(Ar.errUnreachable)
        is SocketTimeoutException -> ApiException(Ar.errTimeout)
        is SSLException -> ApiException(Ar.errSsl)
        else -> ApiException(Ar.errNetwork)
    }
}
