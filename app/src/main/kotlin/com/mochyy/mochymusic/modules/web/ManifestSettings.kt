// a link or zip module's settings while it runs: the fields its page shows, what people picked, and the look and glass those picks bring

package com.mochyy.mochymusic.modules.web

import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.modules.skin.SkinGlass

internal class ManifestSettings(manifest: WebModuleManifest, skip: Set<String> = emptySet()) {

    private val settings = manifest.settings.filter { it.key.isNotBlank() && it.key.lowercase() !in skip }

    val fields: List<ModuleField> = settings.map { it.toField() }

    /** the settings drawn as sliders, by key */
    val ranges: Map<String, FieldRange> = settings.mapNotNull { setting -> setting.range?.let { setting.key to it } }.toMap()

    @Volatile
    var values: Map<String, String> = emptyMap()

    /** what each setting is right now, its default until someone changes it */
    fun current(): Map<String, String> = fields.associate { field -> field.id to (values[field.id] ?: field.default) }

    /** the looks the picked options and switched-on toggles bring, laid over [base] in the manifest's order */
    fun look(base: Map<String, String>): Map<String, String> {
        val now = current()
        return settings.fold(base) { look, setting -> look + setting.lookAt(now[setting.key]) }
    }

    /** the module's glass with whatever the picked options and switched-on toggles change about it, in the manifest's order */
    fun glass(base: SkinGlass?): SkinGlass? {
        base ?: return null
        val now = current()
        return settings.fold(base) { glass, setting -> setting.glassAt(now[setting.key])?.let(glass::with) ?: glass }
    }
}
