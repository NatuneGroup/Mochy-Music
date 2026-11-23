package com.mochyy.mochymusic.lyrics

import java.util.Locale

internal actual fun languageDisplayName(tag: String): String? =
    try {
        Locale.forLanguageTag(tag).displayLanguage
    } catch (e: Exception) {
        null
    }
