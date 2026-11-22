package com.mochyy.mochymusic.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

// skip community-marked segments (sponsor reads, off-topic music) in YouTube songs
val SponsorBlockEnabledKey = booleanPreferencesKey("sponsorBlockEnabled")
val SponsorBlockCategoriesKey = stringSetPreferencesKey("sponsorBlockCategories")
