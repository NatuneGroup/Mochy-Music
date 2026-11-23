// a module's own files: the pictures, fonts and shaders its zip brought, and where a path in its manifest points

package com.mochyy.mochymusic.modules.web

/** the files one module brought in its zip, kept in the app's own storage; the platform says what a file is (an android File, a path elsewhere) */
interface ModuleFiles {
    /** the module's file at [inside], a path already checked to stay in its folder */
    fun asset(inside: String): Any

    /** the same file as text, for the module api's icon and banner and anything that only takes a string */
    fun path(asset: Any): String?
}

/** a slider's ends and its notch, [step] null for a smooth one */
data class FieldRange(val start: Float, val end: Float, val step: Float?)

/** a module whose settings include sliders, which mochy draws from its manifest; the module api itself only knows text, lists and switches */
interface FieldRanges {
    val ranges: Map<String, FieldRange>
}

/** a path from a manifest as a path inside the module's folder, or null when it isn't one: a link as it is, a `..` never gets out */
internal fun modulePathInside(path: String?): String? {
    val name = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return name.trimStart('/').takeIf { ".." !in it.split('/') }
}

/** a picture or font a module names: a link as it is, a path from the module's zip, or a path next to a link module's manifest */
fun resolveModulePath(path: String?, files: ModuleFiles?, base: String?): Any? {
    val name = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (name.startsWith("https://") || name.startsWith("http://")) return name
    // plain string work, no disk: this runs while things draw, and a path can't climb out of the module's folder
    val inside = modulePathInside(name) ?: return null
    if (files != null) return files.asset(inside)
    return base?.let { "$it/$inside" }
}

/** the same as text, the way the module api carries a picture: a link, or a file's own path */
fun resolveModuleArt(path: String?, files: ModuleFiles?, base: String?): String? = when (val found = resolveModulePath(path, files, base)) {
    null -> null
    is String -> found
    else -> files?.path(found)
}

/** the module's own file a path names, for a font or a shader; only a zip carries files, so a link has none */
internal fun moduleFile(path: String?, files: ModuleFiles?): Any? {
    if (files == null) return null
    if (path?.trim()?.startsWith("https://") == true || path?.trim()?.startsWith("http://") == true) return null
    return modulePathInside(path)?.let(files::asset)
}
