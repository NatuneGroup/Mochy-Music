// AndroidImportMatchStore.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import java.io.File

/** the app's own file for the version picks, written the way it always was: to a temp file, then renamed over */
class AndroidImportMatchStore(context: Context) : ImportMatchStore {
    private val file = File(context.applicationContext.filesDir, ImportMatches.FILE_NAME)

    override fun exists(): Boolean = file.exists()

    override fun read(): String? = runCatching { file.readText() }.getOrNull()

    override fun write(text: String) {
        // write-then-rename so a crash mid-write never leaves half a file
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }
}
