// DNS over HTTPS, from the Internet settings page: a resolver the app's clients consult instead
// of the system's, held behind a volatile so a change needs no client rebuild, the same trick
// the proxy plays. Without it the system resolver answers, as before.

package com.mochyy.mochymusic.utils

import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.constants.DnsOverHttpsCustomUrlKey
import com.mochyy.mochymusic.constants.DnsOverHttpsEnabledKey
import com.mochyy.mochymusic.constants.DnsOverHttpsProviderKey
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

object AppDns {
    /** the resolver in force: the DoH one while it is on and its provider answers, the system's otherwise */
    @Volatile
    var current: Dns = Dns.SYSTEM
        private set

    /** the provider names the settings page offers, in the order it shows them */
    val providers = listOf("Cloudflare", "Google", "AdGuard", "Quad9", "Custom")

    /** the DoH endpoint a provider name means; null when the name means the system resolver */
    fun providerUrl(provider: String?, customUrl: String?): String? = when (provider) {
        "Google" -> "https://dns.google/dns-query"
        "Cloudflare" -> "https://cloudflare-dns.com/dns-query"
        "AdGuard" -> "https://dns.adguard.com/dns-query"
        "Quad9" -> "https://dns.quad9.net/dns-query"
        "Custom" -> customUrl?.takeIf { it.startsWith("https://") }
        else -> null
    }

    /** reads the settings once (at start-up) and builds the resolver they ask for */
    fun configure(settings: Preferences) {
        val enabled = settings[DnsOverHttpsEnabledKey] == true
        val url = providerUrl(settings[DnsOverHttpsProviderKey], settings[DnsOverHttpsCustomUrlKey])
        current = if (enabled && url != null) build(url) else Dns.SYSTEM
    }

    /** swaps the resolver in place; the shared clients pick it up on their next lookup */
    fun update(enabled: Boolean, provider: String?, customUrl: String?) {
        val url = providerUrl(provider, customUrl)
        val next = if (enabled && url != null) build(url) else Dns.SYSTEM
        if (next !== current) {
            current = next
            // idle connections were resolved the old way; let them go so new ones ask the new way
            AppProxy.connectionPool.evictAll()
        }
    }

    private fun build(url: String): Dns = runCatching {
        DnsOverHttps.Builder()
            // the bootstrap client is a plain one, so resolving the DoH endpoint itself stays on
            // the system resolver rather than looping back through this one
            .client(OkHttpClient())
            .url(url.toHttpUrl())
            .build()
    }.getOrDefault(Dns.SYSTEM)
}

/**
 * Makes this client resolve through the Internet page's DoH choice. The delegate reads the
 * holder at every lookup, so a change in the settings takes effect without rebuilding the
 * client, the same way the proxy's selector does.
 */
fun OkHttpClient.Builder.followAppDns(): OkHttpClient.Builder =
    dns(Dns { host -> AppDns.current.lookup(host) })
