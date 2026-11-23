package com.mochyy.mochymusic.ui.screens.library

// TODO: iOS has no normalization here yet (Foundation's NSString decomposed / precomposed forms would
// do it). Until then accented Latin ("É") files under '#', not under its letter.
internal actual fun normalizeNfkc(text: String): String = text

internal actual fun normalizeNfd(text: String): String = text

// TODO: a range table approximating java.lang.Character.UnicodeScript for the scripts the Library
// sorts by. Latin is only the basic letters and the Latin-1 and Latin Extended blocks.
internal actual fun unicodeScriptNameOf(codePoint: Int): String? = when (codePoint) {
    in 0x0041..0x005A, in 0x0061..0x007A, in 0x00C0..0x00D6, in 0x00D8..0x00F6,
    in 0x00F8..0x024F, in 0x1E00..0x1EFF -> "LATIN"
    in 0x0370..0x03FF -> "GREEK"
    in 0x0400..0x04FF -> "CYRILLIC"
    in 0x0590..0x05FF -> "HEBREW"
    in 0x0600..0x06FF -> "ARABIC"
    in 0x0900..0x097F -> "DEVANAGARI"
    in 0x0980..0x09FF -> "BENGALI"
    in 0x0A00..0x0A7F -> "GURMUKHI"
    in 0x0A80..0x0AFF -> "GUJARATI"
    in 0x0B00..0x0B7F -> "ORIYA"
    in 0x0B80..0x0BFF -> "TAMIL"
    in 0x0C00..0x0C7F -> "TELUGU"
    in 0x0C80..0x0CFF -> "KANNADA"
    in 0x0D00..0x0D7F -> "MALAYALAM"
    in 0x0E00..0x0E7F -> "THAI"
    in 0x3040..0x309F -> "HIRAGANA"
    in 0x30A0..0x30FF, in 0x31F0..0x31FF, in 0xFF66..0xFF9D -> "KATAKANA"
    in 0x1100..0x11FF, in 0x3130..0x318F, in 0xAC00..0xD7AF -> "HANGUL"
    in 0x3400..0x4DBF, in 0x4E00..0x9FFF, in 0xF900..0xFAFF -> "HAN"
    else -> null
}

// TODO: no ICU pinyin on iOS yet; Chinese titles share bucket 0 and are labelled by their first character.
internal actual fun pinyinBucketOf(text: String): PinyinBucket = PinyinBucket(0, "")
