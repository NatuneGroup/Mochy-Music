// how a look module's settings are laid on mochy's own: a player or glass of its own switches to it, and a fixed accent turns the cover-coloured theme off

package com.mochyy.mochymusic.modules.skin

import com.mochyy.mochymusic.constants.ChromeGlassStyle
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.module.LookSetting

/** the look as it's applied: a module with its own player or glass switches to it, and a fixed accent turns the cover-coloured theme off */
internal fun appliedLook(look: Map<String, String>, skin: ModuleSkin?): Map<String, String> = buildMap {
    putAll(look)
    if (skin?.player != null && LookSetting.PLAYER_STYLE !in look) put(LookSetting.PLAYER_STYLE, PlayerStyle.MODULE.name)
    if (skin?.glass != null && LookSetting.BOTTOM_BAR !in look) put(LookSetting.BOTTOM_BAR, ChromeGlassStyle.MODULE.name)
    if (LookSetting.ACCENT_COLOR in look && LookSetting.DYNAMIC_THEME !in look) put(LookSetting.DYNAMIC_THEME, "false")
}
