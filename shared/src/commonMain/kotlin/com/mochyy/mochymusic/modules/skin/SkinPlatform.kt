// what a platform does with a look's own files: run its shaders, load its fonts, and say whether either works here

package com.mochyy.mochymusic.modules.skin

import androidx.compose.ui.text.font.FontFamily

/**
 * Everything in a look module that only the machine can answer: whether agsl shaders run here, what a file of the
 * module's holds, and how a shader or a font is made from it. The app installs its own through [SkinPlatforms] before
 * the first publish, so a look's glass and font look and fail exactly as they did before; desktop and iOS keep
 * [NoSkinPlatform] until their host brings one, and draw the looks' fallback colours and mochy's own font.
 */
interface SkinPlatform {
    /** whether a look's agsl shaders can run here at all (android 13 and up; nothing else yet) */
    val shaders: Boolean

    /** whether [file], one of the module's assets, is there and readable */
    fun exists(file: Any): Boolean

    /** its length in bytes */
    fun length(file: Any): Long

    /** its text, for a shader */
    fun read(file: Any): String?

    /** a stamp that changes when the file does, so a compiled shader is kept while the file stays the same */
    fun stamp(file: Any): String

    /** [source] compiled, or an exception whose message says what the compiler didn't like */
    fun compile(source: String): Any

    /** [regular] (and [bold]) as a font family, or null when [regular] isn't a font */
    fun font(regular: Any, bold: Any?): FontFamily?

    /** something a look brought couldn't be used, for the log and its page */
    fun warn(tag: String, message: String, problem: Throwable?)
}

/** a platform without shaders or module fonts: the looks still draw their fallback colours and mochy's own font */
object NoSkinPlatform : SkinPlatform {
    override val shaders = false
    override fun exists(file: Any): Boolean = false
    override fun length(file: Any): Long = 0L
    override fun read(file: Any): String? = null
    override fun stamp(file: Any): String = ""
    override fun compile(source: String): Any = error("this platform doesn't run a look's shaders")
    override fun font(regular: Any, bold: Any?): FontFamily? = null
    override fun warn(tag: String, message: String, problem: Throwable?) = Unit
}

/** the platform the looks are drawn with, set by whoever starts the app up, before anything publishes a look */
object SkinPlatforms {
    var current: SkinPlatform = NoSkinPlatform
        private set

    fun install(platform: SkinPlatform) {
        current = platform
    }
}
