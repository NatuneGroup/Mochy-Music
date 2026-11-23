package com.mochyy.mochymusic.eq.autoeq

import com.mochyy.mochymusic.eq.data.ParametricEQ
import com.mochyy.mochymusic.eq.data.ParametricEQParser
import okio.IOException

/** one measured result: a headphone as one reviewer measured it on one rig */
data class AutoEqEntry(
    val name: String,
    val source: String,
    val rig: String?,
    /** the result's folder under autoeq's results/, decoded */
    val path: String,
    val form: HeadphoneForm,
) {
    val sourceLabel: String get() = if (rig != null) "$source · $rig" else source
}

/** every result for one headphone name, the unit the list shows */
data class AutoEqModel(
    val name: String,
    val entries: List<AutoEqEntry>,
) {
    internal val searchKey: String = normalize(name)
    internal val compactKey: String = searchKey.replace(" ", "")
    val form: HeadphoneForm get() = entries.first().form
}

/** a result's measured curve, the target autoeq fitted it to, and autoeq's own 10-band fit */
class AutoEqMeasurement(
    val entry: AutoEqEntry,
    val frequencies: DoubleArray,
    val raw: DoubleArray,
    val target: DoubleArray,
    val preset: ParametricEQ,
)

/**
 * The autoeq database's text: the index's markdown lines and each result's csv and ParametricEQ
 * text, read the same way on every platform. Fetching and the on-disk cache stay in the Android
 * repository (AutoEqRepository), which hands the download's text to this object.
 */
object AutoEqCatalog {

    /** results whose name contains every word typed, best first: names that start with it lead */
    fun search(all: List<AutoEqModel>, query: String): List<AutoEqModel> {
        val q = normalize(query)
        if (q.isBlank()) return all
        val words = q.split(' ').filter { it.isNotEmpty() }
        // "wh1000" finds "wh-1000xm5" too: punctuation and spaces don't have to be typed
        val compact = q.replace(" ", "")
        return all.filter { model ->
            words.all { model.searchKey.contains(it) } || model.compactKey.contains(compact)
        }.sortedBy { if (it.searchKey.startsWith(q) || it.compactKey.startsWith(compact)) 0 else 1 }
    }

    /** one index line per result: a markdown link to ./source/rig/name, then by source on rig; the name and path can hold brackets */
    fun parseIndex(text: String): List<AutoEqModel> {
        val entries = ArrayList<AutoEqEntry>(9000)
        text.lineSequence().forEach { line ->
            if (!line.startsWith("- [")) return@forEach
            val nameEnd = line.indexOf("](./")
            if (nameEnd < 0) return@forEach
            val pathEnd = line.indexOf(") by ", nameEnd)
            if (pathEnd < 0) return@forEach
            val name = line.substring(3, nameEnd)
            val path = percentDecode(line.substring(nameEnd + 4, pathEnd))
            val by = line.substring(pathEnd + 5).trim()
            val on = by.indexOf(" on ")
            val source = if (on >= 0) by.substring(0, on) else by
            val rig = if (on >= 0) by.substring(on + 4) else null
            val rigFolder = path.split('/').getOrNull(1).orEmpty().lowercase()
            val form = when {
                "earbud" in rigFolder -> HeadphoneForm.Earbud
                "in-ear" in rigFolder -> HeadphoneForm.InEar
                else -> HeadphoneForm.OverEar
            }
            entries += AutoEqEntry(name, source, rig, path, form)
        }
        return entries
            .groupBy { it.name }
            .map { (name, list) -> AutoEqModel(name, list) }
            .sortedBy { it.name.lowercase() }
    }

    fun parseMeasurement(entry: AutoEqEntry, csv: String, txt: String): AutoEqMeasurement {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.first().split(',').map { it.trim().lowercase() }
        val fi = header.indexOf("frequency")
        val ri = header.indexOf("raw")
        val ti = header.indexOf("target")
        if (fi < 0 || ri < 0) throw IOException("Unreadable AutoEq measurement")
        val rows = lines.drop(1).mapNotNull { row ->
            val cells = row.split(',')
            val f = cells.getOrNull(fi)?.toDoubleOrNull() ?: return@mapNotNull null
            val r = cells.getOrNull(ri)?.toDoubleOrNull() ?: return@mapNotNull null
            val t = if (ti >= 0) cells.getOrNull(ti)?.toDoubleOrNull() ?: 0.0 else 0.0
            Triple(f, r, t)
        }
        return AutoEqMeasurement(
            entry = entry,
            frequencies = DoubleArray(rows.size) { rows[it].first },
            raw = DoubleArray(rows.size) { rows[it].second },
            target = DoubleArray(rows.size) { rows[it].third },
            preset = ParametricEQParser.parseText(txt),
        )
    }
}

/** percent escapes back to utf-8 text; a literal plus stays a plus (unlike urldecoder) */
fun percentDecode(text: String): String {
    if ('%' !in text) return text
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (ch == '%' && i + 2 <= text.lastIndex) {
            val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.add(hex.toByte())
                i += 3
                continue
            }
        }
        for (byte in ch.toString().encodeToByteArray()) bytes.add(byte)
        i++
    }
    return bytes.toByteArray().decodeToString()
}

/** lowercase letters and digits, runs of anything else folded into one space */
internal fun normalize(text: String): String {
    val out = StringBuilder(text.length)
    var space = true
    for (ch in text) {
        if (ch.isLetterOrDigit()) {
            out.append(ch.lowercaseChar())
            space = false
        } else if (!space) {
            out.append(' ')
            space = true
        }
    }
    return out.trimEnd().toString()
}
