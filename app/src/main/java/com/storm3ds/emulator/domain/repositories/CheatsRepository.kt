package com.storm3ds.emulator.domain.repositories

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import com.storm3ds.emulator.common.cheats.jusrcheat.JusrcheatRepository
import com.storm3ds.emulator.domain.model.Cheat
import com.storm3ds.emulator.domain.model.CheatDatabase
import com.storm3ds.emulator.domain.model.CheatFolder
import com.storm3ds.emulator.domain.model.CheatGameSearchResult
import com.storm3ds.emulator.domain.model.CheatImportProgress
import com.storm3ds.emulator.domain.model.Game
import com.storm3ds.emulator.domain.model.RomInfo
import com.storm3ds.emulator.ui.cheats.model.CheatSubmissionForm

interface CheatsRepository {
    suspend fun getGames(): List<Game>
    suspend fun findGameForRom(romInfo: RomInfo): Game?
    fun getAllGameCheats(game: Game): Flow<List<CheatFolder>>
    fun getFolderCheats(folder: CheatFolder): Flow<List<Cheat>>
    suspend fun getRomEnabledCheats(romInfo: RomInfo): List<Cheat>
    suspend fun updateCheatsStatus(cheats: List<Cheat>)
    suspend fun addCheatFolder(folderName: String, game: Game)
    suspend fun deleteCheatDatabaseIfExists(databaseName: String)
    suspend fun clearAllCheatDatabases()
    suspend fun searchGames(query: String, limit: Int = 50): List<CheatGameSearchResult>
    suspend fun addCheatDatabase(databaseName: String): CheatDatabase
    suspend fun addGameCheats(game: Game): Game
    suspend fun addCheat(folder: CheatFolder, cheat: Cheat)
    suspend fun addCustomCheat(folder: CheatFolder, cheatForm: CheatSubmissionForm)
    suspend fun updateCheat(cheat: Cheat)
    suspend fun deleteCheat(cheat: Cheat)
    fun importCheats(uri: Uri)
    fun isCheatImportOngoing(): Boolean
    fun getCheatImportProgress(): Flow<CheatImportProgress>

    fun getJusrcheatRepository(): JusrcheatRepository? = null
}
