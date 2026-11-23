package com.mochyy.mochymusic.ui.screens.recap

import kotlinx.coroutines.CoroutineDispatcher

/** Dispatchers.IO where the platform has one: the dispatcher the play history is read on. */
internal expect val recapIoDispatcher: CoroutineDispatcher
