// DesktopFiles.kt
// Reading and emptying folders, the two things the storage page and the file dialogs both need. Kept
// in one place so every measurement counts the same files (hidden ones included) and every clear uses
// the same delete (files first, then the folder), which is what makes the page's numbers add up.

package com.mochyy.mochymusic.shared.desktop.files

import java.io.File

/** Bytes a file or a folder takes; a missing path is zero, so a fresh install measures cleanly. */
internal fun sizeOf(file: File): Long = when {
    !file.exists() -> 0L
    file.isFile -> file.length()
    else -> file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

/** Deletes what [directory] holds and leaves the folder itself, so the next write finds it there. */
internal fun clearDirectory(directory: File) {
    val children = directory.listFiles().orEmpty()
    for (child in children) runCatching { child.deleteRecursively() }
}

/** Files in [directory] whose name starts with one of [prefixes]; used for per-song downloads. */
internal fun filesStartingWith(directory: File, prefixes: Collection<String>): List<File> =
    directory.listFiles().orEmpty().filter { file -> prefixes.any { file.name.startsWith(it) } }

/** The display name a folder goes by on a page; the last path segment, or the whole path at a root. */
internal fun folderLabel(file: File): String = file.name.ifBlank { file.absolutePath }
