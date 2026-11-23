// PlatformInfo.nonAndroid.kt

package com.mochyy.mochymusic.core.platform

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Desktop and iOS have no java.time: kotlinx-datetime reads the same github timestamp and the
// month names are written in English, since commonMain has no locale tables.
private val LongMonths = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val ShortMonths = LongMonths.map { it.take(3) }

@OptIn(kotlin.time.ExperimentalTime::class)
private fun dateOf(iso: String): LocalDateTime? = runCatching {
    kotlin.time.Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
}.getOrNull()

actual fun releaseDateText(iso: String): String? {
    val date = dateOf(iso) ?: return null
    return "${LongMonths[date.month.ordinal]} ${date.day}, ${date.year}"
}

actual fun commitDateText(iso: String): String? {
    val date = dateOf(iso) ?: return null
    return "${ShortMonths[date.month.ordinal]} ${date.day}, ${date.year}"
}
