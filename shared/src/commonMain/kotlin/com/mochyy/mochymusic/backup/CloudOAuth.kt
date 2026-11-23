// CloudOAuth.kt
//
// The browser sign-in a dropbox or onedrive backup uses: PKCE, the token exchange, the refresh a
// long-running schedule needs, the account's name and the revoke on sign-out. It used to build
// OkHttp calls; it is Ktor now. The app still owns the client ids (they are build-time values)
// and passes them in, and still opens the same login page in its browser.

package com.mochyy.mochymusic.backup

import com.mochyy.mochymusic.db.nowEpochMillis
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** what a cloud's token endpoint sent back */
class OAuthTokens(val access: String, val refresh: String?, val expiresAt: Long)

/** a cloud mochy signs in to on the cloud's own page in the browser, with pkce, so no secret ships inside mochy */
enum class OAuthApp(
    val kind: CloudKind,
    private val authorizeUrl: String,
    private val tokenUrl: String,
    // where the cloud sends the browser back to; it's registered with the cloud's app and the login activity catches it
    val redirectUri: String,
) {
    Dropbox(
        CloudKind.Dropbox,
        authorizeUrl = "https://www.dropbox.com/oauth2/authorize",
        tokenUrl = "https://api.dropboxapi.com/oauth2/token",
        redirectUri = "mochy-backup://dropbox",
    ),
    OneDrive(
        CloudKind.OneDrive,
        authorizeUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
        tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
        redirectUri = "mochy-backup://onedrive",
    ),
    ;

    /** the page to send the user to; [clientId] is the app key from local.properties (or CI secrets) */
    fun authorizeUri(clientId: String, state: String, challenge: String): String = buildString {
        append(authorizeUrl)
        append("?client_id=").append(clientId.encodeURLParameter())
        append("&response_type=code")
        append("&redirect_uri=").append(redirectUri.encodeURLParameter())
        append("&state=").append(state.encodeURLParameter())
        append("&code_challenge=").append(challenge.encodeURLParameter())
        append("&code_challenge_method=S256")
        when (this@OAuthApp) {
            // offline hands out a refresh token, so backups keep going after the 4 hour access token runs out
            Dropbox -> append("&token_access_type=offline")
            OneDrive -> append("&scope=").append(OneDriveScope.encodeURLParameter()).append("&prompt=select_account")
        }
    }

    suspend fun exchange(clientId: String, code: String, verifier: String): OAuthTokens = token(
        clientId = clientId,
        rejected = CloudProblem.Expired,
        "grant_type" to "authorization_code",
        "code" to code,
        "redirect_uri" to redirectUri,
        "code_verifier" to verifier,
    )

    // a refresh token the cloud turns down means the user removed mochy there or changed their password
    suspend fun refresh(clientId: String, refreshToken: String): OAuthTokens = token(
        clientId = clientId,
        rejected = CloudProblem.SignedOut,
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
    )

    /** the email or name to show for the account that just signed in, the cloud's own name when it won't say */
    suspend fun accountName(accessToken: String): String = runCatching {
        val request = when (this) {
            Dropbox -> CloudRequest(
                method = HttpMethod.Post,
                url = "https://api.dropboxapi.com/2/users/get_current_account",
                headers = mapOf("Authorization" to "Bearer $accessToken"),
                body = ByteArray(0),
                contentType = CloudHttp.Json,
            )
            OneDrive -> CloudRequest(
                method = HttpMethod.Get,
                url = "https://graph.microsoft.com/v1.0/me",
                headers = mapOf("Authorization" to "Bearer $accessToken"),
            )
        }
        val reply = CloudHttp.read(CloudHttp.send(request))
        check(reply.ok)
        val json = runCatching { jsonObjectOf(reply.body) }.getOrNull()
        when (this) {
            Dropbox -> json?.text("email").orEmpty()
                .ifEmpty { json?.objectOrNull("name")?.text("display_name").orEmpty() }
            OneDrive -> json?.text("mail").orEmpty()
                .ifEmpty { json?.text("userPrincipalName").orEmpty() }
                .ifEmpty { json?.text("displayName").orEmpty() }
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: kind.label

    /** tells dropbox to forget the token on sign out; microsoft has nothing like it, its tokens just run out */
    suspend fun revoke(accessToken: String) {
        if (this != Dropbox || accessToken.isEmpty()) return
        runCatching {
            CloudHttp.read(
                CloudHttp.send(
                    CloudRequest(
                        method = HttpMethod.Post,
                        url = "https://api.dropboxapi.com/2/auth/token/revoke",
                        headers = mapOf("Authorization" to "Bearer $accessToken"),
                        body = ByteArray(0),
                        contentType = CloudHttp.Json,
                    ),
                ),
            )
        }
    }

    private suspend fun token(clientId: String, rejected: CloudProblem, vararg fields: Pair<String, String>): OAuthTokens {
        val form = Parameters.build {
            append("client_id", clientId)
            fields.forEach { (name, value) -> append(name, value) }
            if (this@OAuthApp == OneDrive) append("scope", OneDriveScope)
        }
        val reply = CloudHttp.read(
            CloudHttp.send(CloudRequest(method = HttpMethod.Post, url = tokenUrl, form = form)),
        )
        if (reply.code == 400 || reply.code == 401) throw CloudException(rejected)
        if (!reply.ok) CloudHttp.fail(reply.code)
        val json = runCatching { jsonObjectOf(reply.body) }.getOrNull()
            ?: throw CloudException(CloudProblem.Busy)
        val access = json.text("access_token").orEmpty().ifEmpty { throw CloudException(CloudProblem.Busy) }
        val expiresIn = json.text("expires_in")?.toLongOrNull() ?: 0L
        return OAuthTokens(
            access = access,
            refresh = json.text("refresh_token").orEmpty().ifEmpty { null },
            expiresAt = if (expiresIn > 0) nowEpochMillis() + expiresIn * 1000 else 0L,
        )
    }

    companion object {
        // the app folder only: mochy sees its own backups and nothing else in the user's onedrive
        private const val OneDriveScope = "offline_access User.Read Files.ReadWrite.AppFolder"

        fun of(kind: CloudKind): OAuthApp? = entries.find { it.kind == kind }
    }
}

/** keeps a signed-in cloud's access token fresh: renewed a little before it runs out, and once more if the cloud turns it down */
class OAuthSession(
    private val app: OAuthApp,
    /** the app key from local.properties (or CI secrets) */
    private val clientId: String,
    private val store: CloudAccountReader,
) {
    suspend fun send(build: (token: String) -> CloudRequest): io.ktor.client.statement.HttpResponse {
        val token = token()
        val response = CloudHttp.send(build(token))
        if (response.status != HttpStatusCode.Unauthorized) return response
        CloudHttp.drain(response)
        return CloudHttp.send(build(renew(token)))
    }

    suspend fun text(build: (token: String) -> CloudRequest): CloudHttp.Reply =
        CloudHttp.read(send(build))

    private suspend fun token(): String {
        val account = store.current() ?: throw CloudException(CloudProblem.SignedOut)
        if (account.expiresAt == 0L || nowEpochMillis() < account.expiresAt - RenewEarlyMs) return account.accessToken
        return renew(account.accessToken)
    }

    private suspend fun renew(rejected: String): String = Renewing.withLock {
        val account = store.current() ?: throw CloudException(CloudProblem.SignedOut)
        // another backup renewed it while this one waited
        if (account.accessToken != rejected) return@withLock account.accessToken
        val refresh = account.refreshToken.ifEmpty { throw CloudException(CloudProblem.SignedOut) }
        val tokens = app.refresh(clientId, refresh)
        store.update {
            it.copy(accessToken = tokens.access, refreshToken = tokens.refresh ?: it.refreshToken, expiresAt = tokens.expiresAt)
        }
        tokens.access
    }

    private companion object {
        const val RenewEarlyMs = 5 * 60_000L
        val Renewing = Mutex()
    }
}

// ---- reading json bodies, the way org.json's opt* calls did ----

internal fun jsonObjectOf(text: String): JsonObject =
    com.mochyy.mochymusic.api.AiJson.json.parseToJsonElement(text) as JsonObject

internal fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject
