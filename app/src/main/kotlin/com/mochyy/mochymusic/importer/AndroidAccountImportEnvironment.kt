// AndroidAccountImportEnvironment.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import com.mochyy.mochymusic.constants.AccountNameKey
import com.mochyy.mochymusic.extensions.isUserLoggedIn
import com.mochyy.mochymusic.importer.account.AccountImportEnvironment
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get

/** the account session's platform answers: the app's own signed-in check and saved account name */
class AndroidAccountImportEnvironment(private val context: Context) : AccountImportEnvironment {
    private val appContext = context.applicationContext

    override fun isUserLoggedIn(): Boolean = appContext.isUserLoggedIn()

    override suspend fun youTubeAccountName(): String? =
        appContext.dataStore.get(AccountNameKey, "").ifBlank { null }
}
