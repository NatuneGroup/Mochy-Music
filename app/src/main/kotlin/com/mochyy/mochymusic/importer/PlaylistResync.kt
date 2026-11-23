// PlaylistResync.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.importer.account.AccountService
import com.mochyy.mochymusic.ui.screens.settings.importsync.PlaylistResyncGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android's Re-sync entry point: the routing and reading live in shared PlaylistResyncEngine; this
 * keeps the Hilt singleton the menu and the account page already reach and gives the engine the
 * app's settings and the one import service, so a re-sync joins the same queue as an import.
 */
@Singleton
class PlaylistResync @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    importService: MusicImportService,
) : PlaylistResyncGateway {
    private val engine = PlaylistResyncEngine(database, importService.engine, AndroidImportSettings(context))

    suspend fun start(playlistId: String): ResyncStart = engine.start(playlistId)

    suspend fun startAll(): ResyncAllStart = engine.startAll()

    override suspend fun startFromLink(playlistId: String, link: String): ResyncStart = engine.startFromLink(playlistId, link)

    override suspend fun startWithReads(reads: Map<String, ResyncRead>): ResyncStart = engine.startWithReads(reads)

    override suspend fun target(playlistId: String): ResyncTarget? = engine.target(playlistId)

    override suspend fun accountTargets(service: AccountService): List<ResyncTarget> = engine.accountTargets(service)
}
