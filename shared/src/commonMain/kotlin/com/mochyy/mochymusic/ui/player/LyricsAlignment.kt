package com.mochyy.mochymusic.ui.player

/**
 * Which side of the screen a line sits on.
 *
 * Word-synced providers mark duet and call-and-response lines with `{agent:v1}` / `{agent:v2}`,
 * which LyricsUtils has always parsed into [com.mochyy.mochymusic.lyrics.LyricsEntry.agent]. Until
 * now only the three unused renderers (MetroLyrics, MochyLyrics, LyricsV2) did anything with it,
 * so in the live player every voice rendered flush left and duets were impossible to follow.
 */
enum class LyricsAlignment { START, CENTER, END }
