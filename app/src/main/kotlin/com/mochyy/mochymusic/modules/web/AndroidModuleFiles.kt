// the files a zip module brought, in the app's own storage, handed to the shared skin parser as the platform's own file type

package com.mochyy.mochymusic.modules.web

import java.io.File

/** [folder] as the shared `ModuleFiles` sees it: every path in a manifest becomes a plain java File, what Coil and the font loaders already take */
class AndroidModuleFiles(private val folder: File) : ModuleFiles {
    override fun asset(inside: String): Any = File(folder, inside)

    override fun path(asset: Any): String? = (asset as? File)?.path
}
