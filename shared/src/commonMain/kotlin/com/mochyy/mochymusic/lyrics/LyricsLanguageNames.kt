// LyricsLanguageNames.kt
//
// A language tag's name in the device's language ("fr" -> "French"), the fallback LyricsTranslationHelper
// used java.util.Locale for on Android. Kept behind a small platform call so common code can use it.

package com.mochyy.mochymusic.lyrics

import com.mochyy.mochymusic.constants.LanguageCodeToName

internal expect fun languageDisplayName(tag: String): String?

/**
 * The name sent to the translation back ends for [targetLanguage]: the app's own table first, then
 * the device's name for the tag, then the tag itself.
 */
internal fun translationLanguageName(targetLanguage: String): String =
    LanguageCodeToName[targetLanguage]
        ?: languageDisplayName(targetLanguage)?.takeIf { it.isNotBlank() && it != targetLanguage }
        ?: targetLanguage
