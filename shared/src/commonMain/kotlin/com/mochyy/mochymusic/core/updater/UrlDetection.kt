// UrlDetection.kt

package com.mochyy.mochymusic.core.updater

// the urls inside a release's notes, with where they sit, so the changelog can make them tappable
fun String.extractUrls(): List<Pair<IntRange, String>> {
    val urlPattern = Regex("(?:^|[\\s])((https?://|www\\.|pic\\.)[\\w-]+(\\.[\\w-]+)+([/?].*)?)")
    val urlList = mutableListOf<Pair<IntRange, String>>()

    urlPattern.findAll(this).forEach { match ->
        val group = match.groups[1] ?: return@forEach
        val url = group.value.trim()
        if (url.isEmpty()) return@forEach
        val fullUrl = if (url.startsWith("http")) url else "https://$url"
        urlList.add(group.range to fullUrl)
    }

    return urlList
}
