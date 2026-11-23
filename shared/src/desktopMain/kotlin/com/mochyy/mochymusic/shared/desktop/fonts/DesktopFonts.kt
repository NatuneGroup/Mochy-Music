// DesktopFonts.kt
// Settings > Appearance > Custom font, on a desktop. The shared appearance page asks its host for a
// picker; here the picker is the platform's file dialog and the picked file is copied into Mochy's own
// font folder, under the same rule Android uses: the preference keeps the file's name inside the app,
// not the path the user picked, so the font survives the original being moved or deleted.

package com.mochyy.mochymusic.shared.desktop.fonts

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.mochyy.mochymusic.shared.desktop.DesktopPaths
import com.mochyy.mochymusic.shared.desktop.files.DesktopFileDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File

internal object DesktopFonts {
    val directory: File get() = DesktopPaths.appData.resolve("fonts")

    /** Opens the font chooser, copies the choice in, and answers the name to save; null when cancelled. */
    suspend fun pickAndImport(): String? {
        val picked = withContext(Dispatchers.Swing) {
            DesktopFileDialogs.pickFile("Choose a font", listOf("ttf", "otf"))
        } ?: return null
        return withContext(Dispatchers.IO) { import(picked) }
    }

    /** Copies [source] into the font folder; a name that already exists is reused, not overwritten. */
    fun import(source: File): String? {
        if (!source.isFile) return null
        val targetDir = directory.apply { mkdirs() }
        // the extension comes from the file, the stem is sanitised so any name is a safe file name
        val extension = source.extension.lowercase().takeIf { it in SupportedExtensions } ?: return null
        val stem = source.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "custom_font" }
        val name = "$stem.$extension"
        val target = targetDir.resolve(name)
        if (target.exists()) return name
        return runCatching {
            source.copyTo(target)
            name
        }.getOrNull()
    }

    fun remove(name: String) {
        if (name.isBlank()) return
        runCatching { directory.resolve(name).delete() }
    }

    /** The family for a stored regular (and optional bold) name; null when neither file is readable. */
    fun family(regularName: String, boldName: String): FontFamily? {
        val regular = fileFor(regularName) ?: return null
        val fonts = buildList {
            add(Font(regular, FontWeight.Normal, FontStyle.Normal))
            fileFor(boldName)?.let { add(Font(it, FontWeight.Bold, FontStyle.Normal)) }
        }
        return FontFamily(fonts)
    }

    private fun fileFor(name: String): File? {
        if (name.isBlank()) return null
        val file = directory.resolve(name)
        return file.takeIf { it.isFile && it.length() > 0 }
    }

    private val SupportedExtensions = setOf("ttf", "otf")
}
