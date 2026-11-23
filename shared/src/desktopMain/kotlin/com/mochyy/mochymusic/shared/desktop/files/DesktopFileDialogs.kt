// DesktopFileDialogs.kt
// The three dialogs a desktop app asks its platform for: open a file, choose a folder, and save a
// file under a suggested name. They use the platform's own chooser (Swing's JFileChooser for files
// and folders, AWT's FileDialog for saving), which is what Compose Desktop's own samples do and what
// keeps them working on Linux, macOS and Windows without a native binding.

package com.mochyy.mochymusic.shared.desktop.files

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal object DesktopFileDialogs {
    /** A file to open, filtered to [extensions] when some are given; null when the user cancelled. */
    fun pickFile(title: String, extensions: List<String> = emptyList()): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            if (extensions.isNotEmpty()) {
                fileFilter = FileNameExtensionFilter(extensions.joinToString(", ") { ".$it" }, *extensions.toTypedArray())
            }
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /** A folder to use as a place; null when the user cancelled. */
    fun pickFolder(title: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /** Where to save a file; null when the user cancelled. */
    fun saveFile(title: String, suggestedName: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
            file = suggestedName
            isVisible = true
        }
        val directory = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(directory, name)
    }
}
