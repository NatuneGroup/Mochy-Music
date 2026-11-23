// RecapPlatform.ios.kt
// The names and stamps the recap story shows, in the phone's language: NSDateFormatter with the
// locale's own month and weekday names, the way iOS writes dates everywhere else. (The desktop keeps
// the JDK's formatting; Android reads the resources in the app's locale.)

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mochyy.mochymusic.ui.screens.recap

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970

/** A formatter for one template ("LLLL", "EEEE", "yMd", "jm"), localized to the phone. */
private fun localizedFormatter(template: String): NSDateFormatter =
    NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        setLocalizedDateFormatFromTemplate(template)
    }

private fun dateOf(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): NSDate =
    NSDate.dateWithTimeIntervalSince1970(
        LocalDateTime(year, month, day, hour, minute)
            .toInstant(TimeZone.currentSystemDefault())
            .epochSeconds
            .toDouble(),
    )

// built once: the story asks for names on every page, and a formatter is not cheap to make
private val monthNames by lazy {
    val formatter = localizedFormatter("LLLL")
    (1..12).map { month -> formatter.stringFromDate(dateOf(2000, month, 15)) }
}

private val dayNames by lazy {
    val formatter = localizedFormatter("EEEE")
    // 2024-01-01 is a Monday, so index 0 is ISO day 1
    (0..6).map { offset -> formatter.stringFromDate(dateOf(2024, 1, 1 + offset)) }
}

actual fun fullMonthName(month: Int): String = monthNames[month - 1]

actual fun fullDayName(isoDay: Int): String = dayNames[isoDay - 1]

private val receiptDates by lazy { localizedFormatter("yMd") }

private val receiptTimes by lazy { localizedFormatter("jm") }

internal actual fun receiptDateStamp(value: LocalDateTime): String =
    receiptDates.stringFromDate(dateOf(value.year, value.month.number, value.dayOfMonth))

internal actual fun receiptTimeStamp(value: LocalDateTime): String =
    receiptTimes.stringFromDate(dateOf(value.year, value.month.number, value.dayOfMonth, value.hour, value.minute))
