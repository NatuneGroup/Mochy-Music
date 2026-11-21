package com.music.innertube.utils

// The text helpers of Utils.kt that need no JVM, kept in common code so every platform parses the same way.

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) null
            else part.substring(0, splitIndex) to part.substring(splitIndex + 1)
        }
        .toMap()

// some languages write durations with a dot instead of a colon (indonesian "3.45", finnish, danish), so either separator is accepted
private val DURATION_REGEX = Regex("^(?:(\\p{Nd}+)[:.])?(\\p{Nd}+)[:.](\\p{Nd}{2})$")

fun String.parseTime(): Int? {
    val (hours, minutes, seconds) = DURATION_REGEX.matchEntire(trim())?.destructured ?: return null
    return try {
        (hours.toIntOrNull() ?: 0) * 3600 + minutes.toInt() * 60 + seconds.toInt()
    } catch (e: NumberFormatException) {
        null
    }
}

fun isPrivateId(browseId: String): Boolean {
    return browseId.contains("privately")
}
