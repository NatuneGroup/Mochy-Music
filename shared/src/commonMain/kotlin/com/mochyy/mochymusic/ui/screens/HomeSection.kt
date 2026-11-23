// HomeSection.kt
//
// Home's rows, in the order Home shows them, as plain flags and ids so the order can live here
// and read the same on every platform. HomeScreen (still in :app with its cards, hero and menus)
// fills the signals in from its view model and draws the list.

package com.mochyy.mochymusic.ui.screens

import com.mochyy.mochymusic.utils.FeedVideoKind

sealed class HomeSection(val id: String) {
    data object RecentlyPlayed : HomeSection("recently_played")
    data object MostPlayed : HomeSection("most_played")
    data object History : HomeSection("history")
    data object DontForgetTheseSongs : HomeSection("dont_forget_these_songs")
    data object AccountPlaylists : HomeSection("account_playlists")
    data object Pinned : HomeSection("pinned")
    data object FromTheCommunity : HomeSection("from_the_community")
    data object ArtistList : HomeSection("artist_list")
    data object AllTimeHits : HomeSection("all_time_hits")
    data object LiveShows : HomeSection("live_shows")
    data object MusicVideos : HomeSection("music_videos")
    data class Category(val categoryId: String) : HomeSection("category_$categoryId")

    data class HomePageSection(val index: Int, val title: String) : HomeSection("home_page_section_${index}_$title")
}

/** One shelf of YouTube's own home page, before the video shelves have been pulled out of it. */
data class HomePageShelf(val index: Int, val title: String, val isSpeedDial: Boolean = false)

/**
 * What Home has to show this moment, as plain flags. The screen reads each one off a flow; this
 * keeps the order itself free of the flows, so it can be shared.
 */
data class HomeFeedSignals(
    val categoryIds: List<String> = emptyList(),
    val recentlyPlayed: Boolean = false,
    val pinned: Boolean = false,
    val mostPlayed: Boolean = false,
    val communityPlaylists: Boolean = false,
    val recentHistory: Boolean = false,
    val liveShows: Boolean = false,
    val artistList: Boolean = false,
    val accountPlaylists: Boolean = false,
    val forgottenFavorites: Boolean = false,
    val musicVideos: Boolean = false,
    /** YouTube's own home page shelves, by index, speed dial marked */
    val homePageSections: List<HomePageShelf> = emptyList(),
    val feedVideoKinds: Map<Int, FeedVideoKind> = emptyMap(),
    val allTimeHits: Boolean = false,
    /** the Mochyy top style keeps YouTube's speed dial behind the hero, not as a row */
    val speedDialBehindHero: Boolean = true,
)

/**
 * the recommendation rows are dealt out between Home's own rows, the way Apple Music and Spotify
 * mix "because you like" shelves in with your history; YouTube's live and video shelves sit in the
 * rows above, and its speed dial behind the hero when there is one.
 */
fun homeFeedSections(signals: HomeFeedSignals): List<HomeSection> {
    val list = mutableListOf<HomeSection>()

    val categories = signals.categoryIds
    var nextCategory = 0
    fun addCategories(count: Int = 1) = repeat(count) {
        categories.getOrNull(nextCategory)?.let {
            list.add(HomeSection.Category(it))
            nextCategory++
        }
    }

    if (signals.recentlyPlayed) list.add(HomeSection.RecentlyPlayed)
    if (signals.pinned) list.add(HomeSection.Pinned)
    if (signals.mostPlayed) list.add(HomeSection.MostPlayed)
    addCategories()
    if (signals.communityPlaylists) list.add(HomeSection.FromTheCommunity)
    if (signals.recentHistory) list.add(HomeSection.History)
    addCategories()
    // the two video banners sit apart, so the big frames break the feed up instead of piling up
    if (signals.liveShows) list.add(HomeSection.LiveShows)
    addCategories()
    if (signals.artistList) list.add(HomeSection.ArtistList)
    addCategories()
    if (signals.accountPlaylists) list.add(HomeSection.AccountPlaylists)
    addCategories()
    if (signals.forgottenFavorites) list.add(HomeSection.DontForgetTheseSongs)
    addCategories()
    if (signals.musicVideos) list.add(HomeSection.MusicVideos)
    addCategories(categories.size)

    // youtube's live and video shelves sit in the rows above, and its speed dial behind the hero when there is one
    val homePageSections = signals.homePageSections
        .filter { shelf ->
            signals.feedVideoKinds[shelf.index] != FeedVideoKind.Live && signals.feedVideoKinds[shelf.index] != FeedVideoKind.MusicVideos &&
                !(shelf.isSpeedDial && signals.speedDialBehindHero)
        }
    homePageSections.firstOrNull()?.let { list.add(HomeSection.HomePageSection(it.index, it.title)) }
    if (signals.allTimeHits) list.add(HomeSection.AllTimeHits)
    homePageSections.drop(1).forEach { list.add(HomeSection.HomePageSection(it.index, it.title)) }

    return list
}
