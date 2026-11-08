package com.saydlawy.sahab.data

import com.saydlawy.sahab.util.Ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
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
        val base = try {
            "${account.server}/player_api.php".toHttpUrl()
        } catch (e: IllegalArgumentException) {
            throw ApiException(Ar.errBadUrl)
        }
        val url = base.newBuilder().apply {
            addQueryParameter("username", account.user)
            addQueryParameter("password", account.pass)
            if (action != null) addQueryParameter("action", action)
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        val text = client.newCall(Request.Builder().url(url).build()).awaitBody()
        return withContext(Dispatchers.Default) {
            try {
                ApiJson.parseToJsonElement(text)
            } catch (e: Exception) {
                throw ApiException(Ar.errBadResponse)
            }
        }
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
