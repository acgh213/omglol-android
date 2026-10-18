package lol.omg.pocket.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Base URL override is internal and used by local HTTP contract tests only. */
class OmgApi internal constructor(private val baseUrl: HttpUrl) {
    constructor() : this("https://api.omg.lol/".toHttpUrl())
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()

    suspend fun account(c: Credentials): Account = parse {
        val obj = get(c, "account", c.email, "info").asJsonObject
        Account(obj.required("name"), obj.required("email"), obj.objectOrNull("created")?.text("relative_time") ?: "Unknown")
    }
    suspend fun addresses(c: Credentials): List<Address> = parse {
        get(c, "account", c.email, "addresses").asJsonArray.map {
            val obj = it.asJsonObject
            Address(obj.required("address"), expiration(obj.objectOrNull("expiration")))
        }
    }
    suspend fun address(c: Credentials, name: String): AddressDetail = parse {
        val obj = get(c, "address", name, "info").asJsonObject
        val returnedName = obj.required("address")
        if (returnedName != name) throw ApiFailure(FailureKind.FORMAT)
        AddressDetail(returnedName, obj.objectOrNull("registration")?.text("relative_time") ?: "Unknown",
            expiration(obj.objectOrNull("expiration")), obj.objectOrNull("verification")?.get("verified")?.takeUnless { it.isJsonNull }?.asBoolean)
    }
    suspend fun statuses(address: String): List<StatusEntry> = parse {
        val response = get(null, "address", address, "statuses", "").asJsonObject
        val statuses = response.get("statuses")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw ApiFailure(FailureKind.FORMAT)
        statuses.map { value ->
            val obj = value.asJsonObject
            val owner = obj.required("address")
            if (owner != address) throw ApiFailure(FailureKind.FORMAT)
            val content = obj.get("content")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?: throw ApiFailure(FailureKind.FORMAT)
            val created = obj.get("created")?.takeIf {
                it.isJsonPrimitive && (it.asJsonPrimitive.isString || it.asJsonPrimitive.isNumber)
            }?.asString
            val epoch = created?.toLongOrNull()?.takeIf { seconds ->
                runCatching { java.time.Instant.ofEpochSecond(seconds).atZone(java.time.ZoneOffset.UTC) }.isSuccess
            }
            StatusEntry(obj.required("id"), owner, obj.text("emoji") ?: "", content, epoch)
        }.sortedWith(compareByDescending<StatusEntry> { it.createdEpochSeconds })
    }
    suspend fun createStatus(c: Credentials, address: String, draft: StatusDraft): PostedStatus = parse {
        val body = JsonObject().apply {
            addProperty("emoji", draft.emoji)
            addProperty("content", draft.content)
            addProperty("skip_mastodon_post", !draft.crossPost)
        }.toString()
        val response = request(c, arrayOf("address", address, "statuses", ""), body).asJsonObject
        PostedStatus(response.required("id"), address)
    }
    suspend fun profile(c: Credentials, address: String): ProfileSource = parse {
        val obj = get(c, "address", address, "web").asJsonObject
        val content = obj.get("content")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: throw ApiFailure(FailureKind.FORMAT)
        ProfileSource(content, obj.text("type") ?: "profile", obj.text("theme") ?: "", obj.text("css") ?: "",
            obj.text("head") ?: "", obj.text("metadata") ?: "", obj.text("branding") ?: "", obj.text("pfp") ?: "")
    }
    private suspend fun get(c: Credentials?, vararg segments: String): JsonElement = request(c, segments)
    private suspend fun request(c: Credentials?, segments: Array<out String>, body: String? = null): JsonElement {
        val url = baseUrl.newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()
        val request = Request.Builder().url(url).apply { if (c != null) header("Authorization", "Bearer ${c.apiKey}") }
            .header("Accept", "application/json").apply {
                if (body == null) get() else post(OneShotJsonBody(body))
            }.build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(ApiFailure(FailureKind.NETWORK))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw failure(it.code)
                            // Bound memory use; responses are account metadata, not arbitrary files.
                            val source = it.body?.source() ?: throw ApiFailure(FailureKind.FORMAT)
                            source.request(1_048_577)
                            if (source.buffer.size > 1_048_576) throw ApiFailure(if (c == null) FailureKind.TOO_LARGE else FailureKind.FORMAT)
                            val root = JsonParser.parseString(source.readUtf8()).asJsonObject
                            val meta = root.getAsJsonObject("request") ?: throw ApiFailure(FailureKind.FORMAT)
                            if (meta.get("success")?.asBoolean != true) throw failure(meta.get("status_code")?.asInt ?: 500)
                            root.get("response")?.takeUnless { value -> value.isJsonNull } ?: throw ApiFailure(FailureKind.FORMAT)
                        }
                    }
                    if (continuation.isActive) result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(if (it is ApiFailure) it else if (it is IOException) ApiFailure(FailureKind.NETWORK) else ApiFailure(FailureKind.FORMAT)) }
                    )
                }
            })
        }
    }
    private suspend fun <T> parse(block: suspend () -> T): T = try { block() }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: ApiFailure) { throw e }
    catch (_: Exception) { throw ApiFailure(FailureKind.FORMAT) }

    private fun failure(status: Int) = ApiFailure(when (status) {
        401, 403 -> FailureKind.AUTH
        429 -> FailureKind.RATE_LIMIT
        400, 404, 409, 413, 415, 422 -> FailureKind.REJECTED
        else -> FailureKind.SERVER
    })
    private fun JsonObject.text(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
    private fun JsonObject.required(key: String): String = text(key)?.takeIf { it.isNotBlank() } ?: throw ApiFailure(FailureKind.FORMAT)
    private fun JsonObject.objectOrNull(key: String): JsonObject? = get(key)?.takeUnless { it.isJsonNull }?.asJsonObject
    private fun expiration(obj: JsonObject?): String = when {
        obj == null -> "Unknown"
        obj.get("expired")?.asBoolean == true -> "Expired"
        obj.get("will_expire")?.asBoolean == false -> "Does not expire"
        else -> obj.text("relative_time")?.let { "Expires in $it" } ?: obj.text("message") ?: "Unknown"
    }
}

/** Also prevents HTTP follow-ups (e.g. 503 Retry-After: 0) from replaying a status. */
private class OneShotJsonBody(value: String) : RequestBody() {
    private val delegate = value.toRequestBody("application/json; charset=utf-8".toMediaType())
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun writeTo(sink: okio.BufferedSink) = delegate.writeTo(sink)
    override fun isOneShot() = true
}
