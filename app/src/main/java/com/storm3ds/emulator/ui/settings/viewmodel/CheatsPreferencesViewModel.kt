package com.storm3ds.emulator.ui.settings.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import com.storm3ds.emulator.common.cheats.BundledMmjCheatsLoader
import com.storm3ds.emulator.common.network.SharedOkHttpClient
import com.storm3ds.emulator.database.MelonDatabase
import com.storm3ds.emulator.domain.model.CheatGameSearchResult
import com.storm3ds.emulator.domain.model.CheatImportProgress
import com.storm3ds.emulator.domain.repositories.CheatsRepository
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class OnlineCheatsDownloadResult {
    data object OnlineUsrCheatImportStarted : OnlineCheatsDownloadResult()
    data class EmbeddedFallbackLoaded(val gamesCount: Int) : OnlineCheatsDownloadResult()
    data object Failed : OnlineCheatsDownloadResult()
}

@HiltViewModel
class CheatsPreferencesViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val cheatsRepository: CheatsRepository,
    private val database: MelonDatabase,
) : ViewModel() {

    companion object {
        const val PREF_CHEATS_DOWNLOAD_MIRROR = "cheats_download_mirror"
        const val PREF_CHEATS_DB_LAST_UPDATED_MS = "cheats_db_last_updated_ms"

        const val MIRROR_AUTO = "auto"
        const val MIRROR_GITHUB_RAW = "github_raw"
        const val MIRROR_JSDELIVR = "jsdelivr"
        const val MIRROR_GHPROXY = "ghproxy"
    }

    fun importCheatsDatabase(databaseUri: Uri) {
        cheatsRepository.importCheats(databaseUri)
    }

    suspend fun getDatabaseStats(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        runCatching {
            val games = database.gameDao().getGameCount()
            val cheats = database.cheatDao().getCheatCount()
            games to cheats
        }.getOrDefault(0 to 0)
    }

    suspend fun clearCheatDatabase() = withContext(Dispatchers.IO) {
        cheatsRepository.clearAllCheatDatabases()
        PreferenceManager.getDefaultSharedPreferences(appContext)
            .edit()
            .remove(PREF_CHEATS_DB_LAST_UPDATED_MS)
            .apply()
    }

    suspend fun searchGames(query: String, limit: Int = 50): List<CheatGameSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            cheatsRepository.searchGames(query.trim(), limit)
        }.getOrDefault(emptyList())
    }

    fun getDatabaseLastUpdatedMs(): Long {
        return PreferenceManager.getDefaultSharedPreferences(appContext)
            .getLong(PREF_CHEATS_DB_LAST_UPDATED_MS, 0L)
    }

    fun saveDatabaseUpdatedNow() {
        PreferenceManager.getDefaultSharedPreferences(appContext)
            .edit()
            .putLong(PREF_CHEATS_DB_LAST_UPDATED_MS, System.currentTimeMillis())
            .apply()
    }

    private fun resolveCandidateUrls(mirror: String): List<String> {
        val rawPrimary = "https://raw.githubusercontent.com/3song/Hacking-DeadSkullzJr-s-NDS-i--Cheat-Databases/main/Cheat%20Databases/usrcheat.dat"
        val githubRawUrls = listOf(
            rawPrimary,
            "https://github.com/3song/Hacking-DeadSkullzJr-s-NDS-i--Cheat-Databases/raw/main/Cheat%20Databases/usrcheat.dat",
        )
        val jsdelivrUrls = listOf(
            "https://cdn.jsdelivr.net/gh/3song/Hacking-DeadSkullzJr-s-NDS-i--Cheat-Databases@main/Cheat%20Databases/usrcheat.dat",
            rawPrimary,
        )
        val ghproxyUrls = listOf(
            "https://ghfast.top/$rawPrimary",
            "https://gh-proxy.com/$rawPrimary",
            "https://ghproxy.net/$rawPrimary",
        )
        return when (mirror) {
            MIRROR_GITHUB_RAW -> githubRawUrls
            MIRROR_JSDELIVR -> jsdelivrUrls
            MIRROR_GHPROXY -> ghproxyUrls
            else -> (githubRawUrls + jsdelivrUrls + ghproxyUrls).distinct()
        }
    }

    suspend fun downloadAndImportOnlineCheats(mirrorKey: String? = null): OnlineCheatsDownloadResult = withContext(Dispatchers.IO) {
        val imported3dsCount = runCatching {
            BundledMmjCheatsLoader.populateAllBundled3dsCheats(appContext, database)
        }.getOrDefault(0)
        if (imported3dsCount > 0) {
            saveDatabaseUpdatedNow()
            return@withContext OnlineCheatsDownloadResult.EmbeddedFallbackLoaded(imported3dsCount)
        }
        OnlineCheatsDownloadResult.Failed
    }

    fun areCheatsBeingImported(): Boolean {
        return cheatsRepository.isCheatImportOngoing()
    }

    fun observeCheatsImportProgress(): Flow<CheatImportProgress> {
        return cheatsRepository.getCheatImportProgress()
    }
}
