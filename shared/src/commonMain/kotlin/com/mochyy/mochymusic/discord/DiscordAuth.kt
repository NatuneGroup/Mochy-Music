// DiscordAuth.kt
//
// Signing in to Discord on Discord's own page, in the browser. It's OAuth with PKCE, so no secret
// ships inside Mochy, and the password never passes through it. Discord sends the browser back to
// discord-<id>:/authorize/callback, and the app's DiscordLoginActivity hands that URL here.
//
// The tokens themselves live behind [DiscordTokenStore]: Android keeps them in the sealed
// settings store it always used, and a platform without one (desktop, iOS) supplies its keychain.

package com.mochyy.mochymusic.discord

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mochyy.mochymusic.constants.DiscordLoginStateKey
import com.mochyy.mochymusic.constants.DiscordLoginVerifierKey
import com.mochyy.mochymusic.constants.DiscordProfileKey
import com.mochyy.mochymusic.utils.Pkce
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import com.mochyy.mochymusic.db.nowEpochMillis
import com.mochyy.mochymusic.utils.ioDispatcher

/** Where a Discord sign-in is, for the Discord page. */
sealed interface DiscordLogin {
    data object Idle : DiscordLogin
    /** Discord's page is open in the browser */
    data object InBrowser : DiscordLogin
    /** back from the browser, swapping the code for a token */
    data object Finishing : DiscordLogin
    data class Failed(val message: String) : DiscordLogin
}

/** What Discord's token endpoint gave Mochy: the token itself, the one that renews it, and when it runs out. */
data class DiscordTokens(
    val accessToken: String,
    val refreshToken: String?,
    /** epoch millis, 0 when Discord said nothing about it */
    val expiresAt: Long,
)

/**
 * The signed-in tokens. Android's settings store seals them (they are its secret keys); a platform
 * without a sealed store uses its own keychain, never plain preferences.
 */
interface DiscordTokenStore {
    /** true while a token is stored, for the account switch on the Discord page */
    val signedIn: Flow<Boolean>

    suspend fun read(): DiscordTokens?

    suspend fun save(tokens: DiscordTokens)

    suspend fun clear()
}

class DiscordAuth(
    private val preferences: DataStore<Preferences>,
    private val tokens: DiscordTokenStore,
    /** the application id from the Discord developer portal (BuildConfig.DISCORD_CLIENT_ID on Android) */
    val clientId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val refreshing = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _login = MutableStateFlow<DiscordLogin>(DiscordLogin.Idle)
    val login: StateFlow<DiscordLogin> = _login.asStateFlow()

    val isConfigured: Boolean get() = clientId.isNotBlank()

    val redirectUri: String get() = "discord-$clientId:/authorize/callback"

    val signedIn: Flow<Boolean> = tokens.signedIn.distinctUntilChanged()

    val profile: Flow<DiscordProfile?> = preferences.data
        .map { it[DiscordProfileKey] }
        .distinctUntilChanged()
        .map { DiscordProfile.fromJson(it) }

    /**
     * The page to send the user to. The check value and the PKCE verifier are saved first, since
     * the phone may close Mochy while the browser is in front.
     */
    suspend fun loginUri(): String {
        val state = Pkce.randomCode(24)
        val verifier = Pkce.randomCode(64)
        preferences.edit {
            it[DiscordLoginStateKey] = state
            it[DiscordLoginVerifierKey] = verifier
        }
        _login.value = DiscordLogin.InBrowser
        return buildString {
            append("https://discord.com/oauth2/authorize")
            append("?client_id=").append(percentEncode(clientId))
            append("&response_type=code")
            append("&redirect_uri=").append(percentEncode(redirectUri))
            append("&scope=").append(percentEncode(Scopes))
            append("&state=").append(percentEncode(state))
            append("&code_challenge=").append(percentEncode(Pkce.challengeOf(verifier)))
            append("&code_challenge_method=S256")
        }
    }

    /** For the login activity, which closes straight away while this carries on. */
    fun handleRedirect(url: String) {
        scope.launch { finish(url) }
    }

    /** The user came back from the browser, ready or not. */
    suspend fun finish(url: String) {
        val prefs = preferences.data.first()
        val expectedState = prefs[DiscordLoginStateKey]
        val verifier = prefs[DiscordLoginVerifierKey]
        // a link that doesn't belong to the login we started is ignored, not trusted
        if (expectedState == null || verifier == null || queryParameter(url, "state") != expectedState) {
            _login.value = DiscordLogin.Failed("That sign-in link had expired. Try logging in again.")
            return
        }
        preferences.edit {
            it.remove(DiscordLoginStateKey)
            it.remove(DiscordLoginVerifierKey)
        }
        val code = queryParameter(url, "code")
        if (code == null) {
            _login.value = DiscordLogin.Failed(
                if (queryParameter(url, "error") == "access_denied") {
                    "You said no on Discord's page, so Mochy isn't connected."
                } else {
                    "Discord didn't let the sign-in through. Try again."
                },
            )
            return
        }

        _login.value = DiscordLogin.Finishing
        try {
            val reply = DiscordHttp.postForm(
                "/oauth2/token",
                mapOf(
                    "client_id" to clientId,
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to redirectUri,
                    "code_verifier" to verifier,
                ),
            )
            check(reply.ok) { "token exchange failed: HTTP ${reply.code} ${reply.body.take(200)}" }
            val token = saveTokens(reply.body)
            val profile = DiscordProfile.fetch(token)
            preferences.edit { it[DiscordProfileKey] = profile.toJson() }
            _login.value = DiscordLogin.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Discord sign-in didn't finish (${e.message})")
            _login.value = DiscordLogin.Failed("Couldn't finish signing in to Discord. Check your connection and try again.")
        }
    }

    /** The user closed the browser without finishing; the page goes back to its button. */
    fun cancelWaiting() {
        if (_login.value == DiscordLogin.InBrowser) _login.value = DiscordLogin.Idle
    }

    fun dismissProblem() {
        if (_login.value is DiscordLogin.Failed) _login.value = DiscordLogin.Idle
    }

    /** A token that still works, renewed first when it's about to run out. Null when signed out. */
    suspend fun accessToken(): String? {
        val stored = tokens.read() ?: return null
        val expiresAt = stored.expiresAt
        if (expiresAt == 0L || nowEpochMillis() < expiresAt - RenewEarlyMs) return stored.accessToken
        return renew(stored.accessToken)
    }

    /**
     * Discord turned [rejected] down. Renew it once; if Discord says the login itself is gone
     * (the user removed Mochy from their authorised apps), sign out so the page says so.
     */
    suspend fun renew(rejected: String): String? = refreshing.withLock {
        val stored = tokens.read() ?: return@withLock null
        // someone else renewed it while this waited
        if (stored.accessToken != rejected) return@withLock stored.accessToken
        val refreshToken = stored.refreshToken ?: return@withLock null.also { signOutHere() }
        val reply = try {
            DiscordHttp.postForm(
                "/oauth2/token",
                mapOf(
                    "client_id" to clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline: keep the old one and try again next time
            return@withLock stored.accessToken
        }
        when {
            reply.ok -> saveTokens(reply.body)
            reply.code in 400..401 -> null.also { signOutHere() }
            else -> stored.accessToken
        }
    }

    /** The name, picture and bio again, for when the page opens: people change them. */
    suspend fun refreshProfile() {
        val token = accessToken() ?: return
        runCatching { DiscordProfile.fetch(token) }
            .onSuccess { profile -> preferences.edit { it[DiscordProfileKey] = profile.toJson() } }
    }

    /** Signs out here and tells Discord to forget the token too. */
    suspend fun logOut() {
        val token = tokens.read()?.accessToken
        signOutHere()
        if (token != null) {
            runCatching {
                DiscordHttp.postForm(
                    "/oauth2/token/revoke",
                    mapOf("client_id" to clientId, "token" to token, "token_type_hint" to "access_token"),
                )
            }
        }
    }

    /**
     * Older Mochy builds logged in by reading the account's own login token out of a web page.
     * That token can do anything the account can, so it's wiped rather than left lying around.
     */
    suspend fun forgetOldLogin() {
        val prefs = preferences.data.first()
        if (LegacyKeys.none { prefs.contains(it) }) return
        preferences.edit { stored -> LegacyKeys.forEach { stored.remove(it) } }
    }

    private suspend fun signOutHere() {
        tokens.clear()
        preferences.edit { it.remove(DiscordProfileKey) }
    }

    /** Stores what Discord's token endpoint sent and returns the new access token. */
    private suspend fun saveTokens(body: String): String {
        val reply = json.parseToJsonElement(body).jsonObject
        val access = reply["access_token"]?.jsonPrimitive?.contentOrNull ?: error("no access token in Discord's reply")
        val refresh = reply["refresh_token"]?.jsonPrimitive?.contentOrNull
        val expiresIn = reply["expires_in"]?.jsonPrimitive?.longOrNull ?: 0L
        tokens.save(
            DiscordTokens(
                accessToken = access,
                refreshToken = refresh,
                expiresAt = if (expiresIn > 0) nowEpochMillis() + expiresIn * 1000 else 0L,
            ),
        )
        return access
    }

    private fun log(message: String) = println("DiscordAuth: $message")

    companion object {
        /**
         * sdk.social_layer_presence is Discord's own bundle for apps that show what the user is
         * doing. Any app can turn it on in the Developer Portal (Social SDK), unlike
         * activities.write on its own, which needs Discord's approval.
         */
        private const val Scopes = "openid identify sdk.social_layer_presence"

        /** renew a little before Discord's deadline, so a long song never meets an expired token */
        private const val RenewEarlyMs = 10 * 60_000L

        private val LegacyKeys: List<Preferences.Key<*>> = listOf(
            "discordToken", "discordUsername", "discordName", "discordAvatar", "discordStatus",
            "discordButton1Text", "discordButton2Text", "discordActivityName",
        ).map { stringPreferencesKey(it) } + listOf(
            "discordInfoDismissed", "discordUseDetails", "discordButton1Visible", "discordButton2Visible",
            "discordAdvancedMode",
        ).map { booleanPreferencesKey(it) }
    }
}

// ---- the url helpers a redirect and a login link need ----

/** One query parameter out of a URL, percent-decoded the way the browser wrote it. */
internal fun queryParameter(url: String, name: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    for (pair in query.split('&')) {
        if (pair.isEmpty()) continue
        val key = pair.substringBefore('=')
        if (percentDecode(key) != name) continue
        return percentDecode(pair.substringAfter('=', ""))
    }
    return null
}

/** `android.net.Uri.encode`'s escaping: everything but letters, digits and `_-!.~'()*` becomes %XX. */
internal fun percentEncode(value: String): String {
    val out = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xff
        val char = code.toChar()
        if (code < 0x80 && (char.isLetterOrDigit() || char in "-_.!~*'()")) {
            out.append(char)
        } else {
            out.append('%').append(HexDigits[code shr 4]).append(HexDigits[code and 0x0f])
        }
    }
    return out.toString()
}

private fun percentDecode(value: String): String {
    if ('%' !in value && '+' !in value) return value
    val out = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val char = value[i]
        when {
            char == '+' -> {
                out.append(' ')
                i++
            }
            char == '%' && i + 2 < value.length -> {
                val byte = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (byte == null) {
                    out.append(char)
                    i++
                } else {
                    out.append(byte.toChar())
                    i += 3
                }
            }
            else -> {
                out.append(char)
                i++
            }
        }
    }
    return out.toString()
}

private const val HexDigits = "0123456789ABCDEF"
