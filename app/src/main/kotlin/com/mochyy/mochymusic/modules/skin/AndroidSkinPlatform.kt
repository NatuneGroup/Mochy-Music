// this phone's way with a look's own files: the shared ActiveSkin asks here for agsl shaders and module fonts

package com.mochyy.mochymusic.modules.skin

import android.os.Build
import androidx.compose.ui.text.font.FontFamily
import com.mochyy.mochymusic.ui.component.createAgslShader
import com.mochyy.mochymusic.ui.theme.fontFamilyFromFiles
import timber.log.Timber
import java.io.File

/** the same things the app's own player, font loader and log already do, so a look looks and fails exactly as it did before */
class AndroidSkinPlatform : SkinPlatform {

    // runtime shaders are android 13+, older phones keep mochy's frost
    override val shaders: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun exists(file: Any): Boolean = (file as? File)?.isFile == true

    override fun length(file: Any): Long = (file as? File)?.length() ?: 0L

    override fun read(file: Any): String? = (file as? File)?.takeIf { it.isFile }?.readText()

    // path, change time and size: a shader swapped under the same name compiles again, one left alone doesn't
    override fun stamp(file: Any): String {
        val found = file as? File ?: return ""
        return "${found.path}:${found.lastModified()}:${found.length()}"
    }

    override fun compile(source: String): Any =
        // never asked below 13 (shaders is false there), and the check is what the annotation on the helper wants to see
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) createAgslShader(source) else error("this phone can't run a look's shaders")

    override fun font(regular: Any, bold: Any?): FontFamily? {
        val file = regular as? File ?: return null
        return fontFamilyFromFiles(file, bold as? File)
    }

    override fun warn(tag: String, message: String, problem: Throwable?) = Timber.tag(tag).w(problem, message)
}
