// AppearanceKeys.kt

package com.mochyy.mochymusic.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

// hide the bottom nav bar while a list scrolls down, show it again on the way up
val HideNavOnScrollKey = booleanPreferencesKey("hideNavOnScroll")

// the accent colour for the app's chrome as an ARGB number; 0 keeps mochy's own grey
val AccentColorKey = intPreferencesKey("accentColor")

// take the accent from the playing song's cover instead of the picked colour
val AccentFromArtKey = booleanPreferencesKey("accentFromArt")

// the whole app's size as a percentage, 100 is the phone's own size
val UiScalePercentKey = intPreferencesKey("uiScalePercent")
