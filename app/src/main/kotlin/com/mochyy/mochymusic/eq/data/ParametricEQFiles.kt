package com.mochyy.mochymusic.eq.data

import java.io.File

/**
 * The two file overloads of [ParametricEQParser] stay on Android: they read a file, and the parser
 * itself is shared so every platform reads the same text format. Call sites keep reading
 * `ParametricEQParser.parseFile(file)`.
 */
fun ParametricEQParser.parseFile(file: File): ParametricEQ {
    if (!file.exists()) {
        throw IllegalArgumentException("File does not exist: ${file.absolutePath}")
    }
    return parseText(file.readText())
}

fun ParametricEQParser.parseFile(filePath: String): ParametricEQ = parseFile(File(filePath))
