package com.saydlawy.sahab.data.auth

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.saydlawy.sahab.data.Account
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.RecentItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CloudPreferences(
    val lightEffects: Boolean = false,
    val homeChannelsLayout: String = "CAROUSEL",
    val homeChannelsSource: String = "AUTO",
    val homeChannelsCategoryId: String = "",
    val moviesLayoutStyle: String = "CLASSIC",
    val seriesLayoutStyle: String = "CLASSIC",
    val updatedAt: Long = 0L,
)

sealed class CloudFetchResult<out T> {
    data class Success<T>(val data: T) : CloudFetchResult<T>()
    data object NotFound : CloudFetchResult<Nothing>()
    data class Error(val cause: Throwable? = null) : CloudFetchResult<Nothing>()
}

sealed class CloudDecryptResult {
    data class Ok(val plain: String) : CloudDecryptResult()
    data object PinRequired : CloudDecryptResult()
    data object WrongPin : CloudDecryptResult()
    data object Invalid : CloudDecryptResult()
}

sealed class CloudAccountRestoreResult {
    data class Restored(val accounts: List<Account>, val migratedFromV1: Boolean = false) : CloudAccountRestoreResult()
    data object NeedsPin : CloudAccountRestoreResult()
    data object WrongPin : CloudAccountRestoreResult()
    data object Empty : CloudAccountRestoreResult()
}

/**
 * تشفير اشتراكات السحابة:
 * - v2: اشتقاق PBKDF2WithHmacSHA256 (100,000 دورة) من Sync PIN يختاره المستخدم + salt عشوائي 16 بايت + AES-GCM.
 * - v1: التوافق مع الوثائق القديمة (`sahab_cloud_v1_$uid`) لفكها وترحيلها تلقائياً إلى v2.
 */
object CloudAccountCrypto {
    private const val PREFIX_V2 = "enc_v2:"
    private const val PREFIX_V1 = "enc_v1:"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    private fun deriveV2Key(pin: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return try {
            val encoded = factory.generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun legacyKeyFor(uid: String): SecretKeySpec {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("sahab_cloud_v1_$uid".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hash, "AES")
    }

    fun encryptV2(plain: String, pin: String, saltOverride: ByteArray? = null): String {
        if (plain.isEmpty()) return ""
        val rng = SecureRandom()
        val salt = saltOverride ?: ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveV2Key(pin, salt), GCMParameterSpec(128, iv))
        }
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val saltB64 = salt.toByteString().base64()
        val payloadB64 = (iv + ct).toByteString().base64()
        return "$PREFIX_V2$saltB64:$payloadB64"
    }

    fun decrypt(value: String, uid: String, pin: String?): CloudDecryptResult {
        if (value.isBlank()) return CloudDecryptResult.Ok("")
        if (value.startsWith(PREFIX_V2)) {
            val cleanPin = pin?.trim().orEmpty()
            if (cleanPin.isEmpty()) return CloudDecryptResult.PinRequired
            val body = value.removePrefix(PREFIX_V2)
            val parts = body.split(':')
            if (parts.size != 2) return CloudDecryptResult.Invalid
            val salt = parts[0].decodeBase64()?.toByteArray() ?: return CloudDecryptResult.Invalid
            val raw = parts[1].decodeBase64()?.toByteArray() ?: return CloudDecryptResult.Invalid
            if (raw.size <= IV_BYTES) return CloudDecryptResult.Invalid
            val iv = raw.copyOfRange(0, IV_BYTES)
            val ct = raw.copyOfRange(IV_BYTES, raw.size)
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, deriveV2Key(cleanPin, salt), GCMParameterSpec(128, iv))
                }
                CloudDecryptResult.Ok(String(cipher.doFinal(ct), Charsets.UTF_8))
            } catch (_: AEADBadTagException) {
                CloudDecryptResult.WrongPin
            } catch (_: Exception) {
                CloudDecryptResult.Invalid
            }
        }
        if (value.startsWith(PREFIX_V1)) {
            return runCatching {
                val raw = value.removePrefix(PREFIX_V1).decodeBase64()?.toByteArray() ?: return CloudDecryptResult.Invalid
                val iv = raw.copyOfRange(0, IV_BYTES)
                val ct = raw.copyOfRange(IV_BYTES, raw.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, legacyKeyFor(uid), GCMParameterSpec(128, iv))
                }
                CloudDecryptResult.Ok(String(cipher.doFinal(ct), Charsets.UTF_8))
            }.getOrElse { CloudDecryptResult.Invalid }
        }
        return CloudDecryptResult.Ok(value)
    }

    fun encryptLegacyV1ForTest(plain: String, uid: String): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, legacyKeyFor(uid), GCMParameterSpec(128, iv))
        }
        val out = iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX_V1 + out.toByteString().base64()
    }
}

class UserSyncService(
    private val syncPinProvider: suspend () -> String? = { null },
) {
    private fun decrypt(value: String, uid: String, pin: String?): String =
        (CloudAccountCrypto.decrypt(value, uid, pin) as? CloudDecryptResult.Ok)?.plain ?: ""
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUid: String?
        get() = auth.currentUser?.uid

    private val _syncWriteErrors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val syncWriteErrors: SharedFlow<String> = _syncWriteErrors.asSharedFlow()

    @Volatile
    private var lastErrorEmitMs: Long = 0L

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { result -> cont.resume(result) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    private suspend fun writeWithRetry(
        label: String,
        uid: String,
        ensureRootDoc: Boolean = true,
        block: () -> Task<Void>,
    ): Boolean {
        val backoffs = longArrayOf(1_000L, 2_000L)
        for (attempt in 0..backoffs.size) {
            try {
                if (ensureRootDoc) {
                    runCatching {
                        awaitTask(
                            db.collection("users").document(uid)
                                .set(mapOf("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                        )
                    }
                }
                awaitTask(block())
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { Log.w("SahabSync", "$label write failed (attempt ${attempt + 1}): ${e.message}") }
                if (attempt < backoffs.size) {
                    delay(backoffs[attempt])
                }
            }
        }
        val now = System.currentTimeMillis()
        if (now - lastErrorEmitMs > 60_000L) {
            lastErrorEmitMs = now
            _syncWriteErrors.tryEmit("تعذر مزامنة التغييرات مع السحابة")
        }
        return false
    }

    suspend fun saveXtreamAccount(account: Account) {
        val uid = currentUid ?: return
        val pin = syncPinProvider()?.trim()?.takeIf { it.isNotEmpty() }
        if (pin == null) {
            // بدون Sync PIN يُحفظ الاشتراك محلياً عبر Android Keystore ولا يُرفع مشفراً بمفتاح مشتق من UID
            runCatching { Log.i("SahabSync", "Skipping cloud Xtream upload: no Sync PIN configured") }
            return
        }
        val data = withContext(Dispatchers.Default) {
            mapOf(
                "server" to CloudAccountCrypto.encryptV2(account.server, pin),
                "user" to CloudAccountCrypto.encryptV2(account.user, pin),
                "pass" to CloudAccountCrypto.encryptV2(account.pass, pin),
                "cryptoVersion" to 2,
            )
        }
        writeWithRetry("xtream", uid) {
            db.collection("users").document(uid).collection("data").document("xtream").set(data)
        }
    }

    suspend fun saveFcmToken(token: String) {
        val uid = currentUid ?: return
        writeWithRetry("fcmToken", uid, ensureRootDoc = false) {
            db.collection("users").document(uid)
                .set(mapOf("fcmToken" to token, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        }
    }


    private val json = Json { ignoreUnknownKeys = true }
    private val favSerializer = ListSerializer(FavItem.serializer())
    private val recentSerializer = ListSerializer(RecentItem.serializer())


    private val accountsSerializer = ListSerializer(Account.serializer())

    suspend fun saveAccounts(accounts: List<Account>, pinOverride: String? = null) {
        val uid = currentUid ?: return
        val pin = (pinOverride ?: syncPinProvider())?.trim()?.takeIf { it.isNotEmpty() }
        if (pin == null) {
            runCatching { Log.i("SahabSync", "Skipping cloud accounts_list upload: no Sync PIN configured (Keystore local-only)") }
            return
        }
        val encryptedPayload = withContext(Dispatchers.Default) {
            val jsonStr = json.encodeToString(accountsSerializer, accounts)
            CloudAccountCrypto.encryptV2(jsonStr, pin)
        }
        writeWithRetry("accounts_list", uid) {
            db.collection("users").document(uid).collection("data").document("accounts_list")
                .set(mapOf("enc" to encryptedPayload, "cryptoVersion" to 2))
        }
    }

    suspend fun restoreAccountsWithPin(pinOverride: String? = null): CloudAccountRestoreResult {
        val uid = currentUid ?: return CloudAccountRestoreResult.Empty
        val pin = (pinOverride ?: syncPinProvider())?.trim()?.takeIf { it.isNotEmpty() }

        val listSnap = runCatching {
            awaitTask(db.collection("users").document(uid).collection("data").document("accounts_list").get())
        }.getOrNull()
        if (listSnap != null && listSnap.exists()) {
            val enc = listSnap.getString("enc")
            val legacyPlain = listSnap.getString("json")
            if (!enc.isNullOrBlank()) {
                when (val dec = withContext(Dispatchers.Default) { CloudAccountCrypto.decrypt(enc, uid, pin) }) {
                    is CloudDecryptResult.Ok -> {
                        val list = runCatching { json.decodeFromString(accountsSerializer, dec.plain) }.getOrDefault(emptyList())
                        if (list.isNotEmpty()) {
                            val wasV1 = enc.startsWith("enc_v1:")
                            if (wasV1 && pin != null) {
                                saveAccounts(list, pin)
                            }
                            return CloudAccountRestoreResult.Restored(list, migratedFromV1 = wasV1)
                        }
                    }
                    is CloudDecryptResult.PinRequired -> return CloudAccountRestoreResult.NeedsPin
                    is CloudDecryptResult.WrongPin -> return CloudAccountRestoreResult.WrongPin
                    is CloudDecryptResult.Invalid -> return CloudAccountRestoreResult.Empty
                }
            } else if (!legacyPlain.isNullOrBlank()) {
                val list = runCatching { json.decodeFromString(accountsSerializer, legacyPlain) }.getOrDefault(emptyList())
                if (list.isNotEmpty()) {
                    if (pin != null) saveAccounts(list, pin)
                    return CloudAccountRestoreResult.Restored(list, migratedFromV1 = true)
                }
            }
        }

        // Fallback إلى وثيقة xtream المفردة
        val xtreamSnap = runCatching {
            awaitTask(db.collection("users").document(uid).collection("data").document("xtream").get())
        }.getOrNull()
        if (xtreamSnap != null && xtreamSnap.exists()) {
            val sEnc = xtreamSnap.getString("server") ?: ""
            val uEnc = xtreamSnap.getString("user") ?: ""
            val pEnc = xtreamSnap.getString("pass") ?: ""
            val (resS, resU, resP) = withContext(Dispatchers.Default) {
                Triple(
                    CloudAccountCrypto.decrypt(sEnc, uid, pin),
                    CloudAccountCrypto.decrypt(uEnc, uid, pin),
                    CloudAccountCrypto.decrypt(pEnc, uid, pin),
                )
            }
            val results = listOf(resS, resU, resP)
            if (results.any { it is CloudDecryptResult.PinRequired }) return CloudAccountRestoreResult.NeedsPin
            if (results.any { it is CloudDecryptResult.WrongPin }) return CloudAccountRestoreResult.WrongPin
            val s = (resS as? CloudDecryptResult.Ok)?.plain.orEmpty()
            val u = (resU as? CloudDecryptResult.Ok)?.plain.orEmpty()
            val p = (resP as? CloudDecryptResult.Ok)?.plain.orEmpty()
            if (s.isNotBlank() && u.isNotBlank()) {
                val acc = Account(server = s, user = u, pass = p)
                val wasV1 = sEnc.startsWith("enc_v1:") || uEnc.startsWith("enc_v1:") || pEnc.startsWith("enc_v1:")
                if (wasV1 && pin != null) {
                    saveAccounts(listOf(acc), pin)
                }
                return CloudAccountRestoreResult.Restored(listOf(acc), migratedFromV1 = wasV1)
            }
        }
        return CloudAccountRestoreResult.Empty
    }

    suspend fun getAccounts(): List<Account>? =
        (restoreAccountsWithPin() as? CloudAccountRestoreResult.Restored)?.accounts

    suspend fun saveFavorites(favs: List<FavItem>) {
        val uid = currentUid ?: return
        val capped = (if (favs.size > MAX_CLOUD_FAVS) favs.take(MAX_CLOUD_FAVS) else favs)
            .map { item -> if (item.url.isBlank()) item else item.copy(url = redactCredentialsFromStreamUrl(item.url)) }
        val jsonStr = json.encodeToString(favSerializer, capped)
        writeWithRetry("favorites", uid) {
            db.collection("users").document(uid).collection("data").document("favorites")
                .set(mapOf("json" to jsonStr))
        }
    }

    suspend fun fetchFavorites(): CloudFetchResult<List<FavItem>> = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(CloudFetchResult.Error())
        db.collection("users").document(uid).collection("data").document("favorites")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    cont.resume(CloudFetchResult.NotFound)
                    return@addOnSuccessListener
                }
                val jsonStr = snapshot.getString("json")
                if (jsonStr == null) {
                    cont.resume(CloudFetchResult.NotFound)
                } else {
                    val parsed = runCatching { json.decodeFromString(favSerializer, jsonStr) }
                    parsed.fold(
                        onSuccess = { cont.resume(CloudFetchResult.Success(it)) },
                        onFailure = { cont.resume(CloudFetchResult.Error(it)) },
                    )
                }
            }
            .addOnFailureListener { e -> cont.resume(CloudFetchResult.Error(e)) }
    }

    suspend fun getFavorites(): List<FavItem>? =
        (fetchFavorites() as? CloudFetchResult.Success)?.data

    suspend fun saveRecents(recents: List<RecentItem>) {
        val uid = currentUid ?: return
        val capped = (if (recents.size > MAX_CLOUD_RECENTS) recents.take(MAX_CLOUD_RECENTS) else recents)
            .map { item -> if (item.url.isBlank()) item else item.copy(url = redactCredentialsFromStreamUrl(item.url)) }
        val jsonStr = json.encodeToString(recentSerializer, capped)
        writeWithRetry("recents", uid) {
            db.collection("users").document(uid).collection("data").document("recents")
                .set(mapOf("json" to jsonStr))
        }
    }

    suspend fun fetchRecents(): CloudFetchResult<List<RecentItem>> = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(CloudFetchResult.Error())
        db.collection("users").document(uid).collection("data").document("recents")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    cont.resume(CloudFetchResult.NotFound)
                    return@addOnSuccessListener
                }
                val jsonStr = snapshot.getString("json")
                if (jsonStr == null) {
                    cont.resume(CloudFetchResult.NotFound)
                } else {
                    val parsed = runCatching { json.decodeFromString(recentSerializer, jsonStr) }
                    parsed.fold(
                        onSuccess = { cont.resume(CloudFetchResult.Success(it)) },
                        onFailure = { cont.resume(CloudFetchResult.Error(it)) },
                    )
                }
            }
            .addOnFailureListener { e -> cont.resume(CloudFetchResult.Error(e)) }
    }

    suspend fun getRecents(): List<RecentItem>? =
        (fetchRecents() as? CloudFetchResult.Success)?.data

    suspend fun getXtreamAccount(): Account? {
        val res = restoreAccountsWithPin()
        return (res as? CloudAccountRestoreResult.Restored)?.accounts?.firstOrNull()
    }

    suspend fun savePreferences(prefs: CloudPreferences) {
        val uid = currentUid ?: return
        val ts = if (prefs.updatedAt > 0L) prefs.updatedAt else System.currentTimeMillis()
        val data = mapOf(
            "lightEffects" to prefs.lightEffects,
            "homeChannelsLayout" to prefs.homeChannelsLayout,
            "homeChannelsSource" to prefs.homeChannelsSource,
            "homeChannelsCategoryId" to prefs.homeChannelsCategoryId,
            "moviesLayoutStyle" to prefs.moviesLayoutStyle,
            "seriesLayoutStyle" to prefs.seriesLayoutStyle,
            "updatedAt" to ts,
        )
        writeWithRetry("preferences", uid) {
            db.collection("users").document(uid).collection("data").document("preferences").set(data)
        }
    }

    suspend fun fetchPreferences(): CloudFetchResult<CloudPreferences> = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(CloudFetchResult.Error())
        db.collection("users").document(uid).collection("data").document("preferences")
            .get()
            .addOnSuccessListener { s ->
                if (s.exists()) {
                    cont.resume(
                        CloudFetchResult.Success(
                            CloudPreferences(
                                lightEffects = s.getBoolean("lightEffects") ?: false,
                                homeChannelsLayout = s.getString("homeChannelsLayout") ?: "CAROUSEL",
                                homeChannelsSource = s.getString("homeChannelsSource") ?: "AUTO",
                                homeChannelsCategoryId = s.getString("homeChannelsCategoryId") ?: "",
                                moviesLayoutStyle = s.getString("moviesLayoutStyle") ?: "CLASSIC",
                                seriesLayoutStyle = s.getString("seriesLayoutStyle") ?: "CLASSIC",
                                updatedAt = s.getLong("updatedAt") ?: 0L,
                            )
                        )
                    )
                } else {
                    cont.resume(CloudFetchResult.NotFound)
                }
            }
            .addOnFailureListener { e -> cont.resume(CloudFetchResult.Error(e)) }
    }

    suspend fun getPreferences(): CloudPreferences? =
        (fetchPreferences() as? CloudFetchResult.Success)?.data

    internal companion object {
        const val MAX_CLOUD_FAVS = 800
        const val MAX_CLOUD_RECENTS = 150
        private val XTREAM_PATH_CREDENTIAL_REGEX = Regex("^https?://[^/]+/(live|movie|series)/[^/]+/[^/]+/.*", RegexOption.IGNORE_CASE)
        private val SENSITIVE_QUERY_PARAM_REGEX = Regex("([?&](?:username|user|password|pass|token)=)[^&#]*", RegexOption.IGNORE_CASE)

        /** Strips Xtream credentials before persisting stream URLs to Firestore; non-credential URLs remain intact. */
        fun redactCredentialsFromStreamUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return ""
            if (XTREAM_PATH_CREDENTIAL_REGEX.matches(trimmed)) {
                return ""
            }
            return trimmed.replace(SENSITIVE_QUERY_PARAM_REGEX, "$1REDACTED")
        }
    }
}
