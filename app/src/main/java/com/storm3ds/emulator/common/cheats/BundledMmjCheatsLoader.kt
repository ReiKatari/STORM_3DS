package com.storm3ds.emulator.common.cheats

import android.content.Context
import com.storm3ds.emulator.database.MelonDatabase
import com.storm3ds.emulator.database.entities.CheatDatabaseEntity
import com.storm3ds.emulator.database.entities.CheatEntity
import com.storm3ds.emulator.database.entities.CheatFolderEntity
import com.storm3ds.emulator.database.entities.GameEntity
import com.storm3ds.emulator.domain.model.Cheat
import java.io.File

object BundledMmjCheatsLoader {
    data class ParsedCheatEntry(
        val name: String,
        val notes: String,
        val code: String,
        val folder: String = "MMJ Bundled",
    )

    fun loadForTitleId(context: Context, titleIdHex: String): List<ParsedCheatEntry> {
        val cleanId = titleIdHex.trim().uppercase().removePrefix("0X").padStart(16, '0')
        if (cleanId.length < 16) return emptyList()
        val assetManager = context.assets
        val candidateNames = assetManager.list("cheats").orEmpty().filter { name ->
            name.endsWith(".txt", ignoreCase = true) &&
                name.substringBefore('.').uppercase().startsWith(cleanId)
        }
        if (candidateNames.isEmpty()) return emptyList()
        val result = mutableListOf<ParsedCheatEntry>()
        for (fileName in candidateNames) {
            runCatching {
                assetManager.open("cheats/$fileName").bufferedReader().use { reader ->
                    result += parseGatewayTxt(reader.readText())
                }
            }
        }
        return result
    }

    fun parseGatewayTxt(raw: String): List<ParsedCheatEntry> {
        val list = mutableListOf<ParsedCheatEntry>()
        var currentName: String? = null
        val notesLines = mutableListOf<String>()
        val codeLines = mutableListOf<String>()
        val hexLineRegex = Regex("""^[0-9A-Fa-f]{8}\s+[0-9A-Fa-f]{8}$""")

        fun flush() {
            val n = currentName?.trim().orEmpty()
            if (n.isNotEmpty() && codeLines.isNotEmpty()) {
                list += ParsedCheatEntry(
                    name = n,
                    notes = notesLines.joinToString("\n"),
                    code = codeLines.joinToString("\n"),
                )
            }
            currentName = null
            notesLines.clear()
            codeLines.clear()
        }

        for (rawLine in raw.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                flush()
                currentName = line.substring(1, line.length - 1).trim()
            } else if (hexLineRegex.matches(line)) {
                if (currentName != null) codeLines += line.uppercase()
            } else if (currentName != null) {
                if (line.startsWith("*")) {
                    notesLines += line.removePrefix("*").trim()
                }
            }
        }
        flush()
        return list
    }

    fun toDomainCheats(entries: List<ParsedCheatEntry>, cheatDatabaseId: Long = 0L): List<Cheat> {
        return entries.mapIndexed { idx, entry ->
            Cheat(
                id = 900000L + idx,
                cheatDatabaseId = cheatDatabaseId,
                name = entry.name,
                description = entry.notes,
                code = entry.code,
                enabled = false,
            )
        }
    }

    suspend fun populate3dsCheatsIfEmpty(
        context: Context,
        database: MelonDatabase,
        titleIdHex: String,
        gameTitle: String,
    ): Boolean {
        val cleanId = titleIdHex.trim().uppercase().removePrefix("0X")
        val normalizedId = if (cleanId.length == 16 && cleanId.all { it in '0'..'9' || it in 'A'..'F' }) {
            cleanId
        } else {
            return false
        }
        val existing = database.gameDao().findGameByCode(normalizedId)
        if (existing?.id != null && database.cheatFolderDao().getFoldersForGame(existing.id).isNotEmpty()) {
            return true
        }

        val entries = mutableListOf<ParsedCheatEntry>()
        val rootBase = File(android.os.Environment.getExternalStorageDirectory(), "STORM 3DS")
        val cheatsDir = File(rootBase, "cheats")
        if (cheatsDir.isDirectory) {
            cheatsDir.listFiles { f ->
                f.isFile && f.name.endsWith(".txt", ignoreCase = true) &&
                    f.nameWithoutExtension.uppercase().startsWith(normalizedId)
            }?.forEach { txtFile ->
                runCatching {
                    entries += parseGatewayTxt(txtFile.readText()).map {
                        it.copy(folder = "Gateway 3DS (${txtFile.name})")
                    }
                }
            }
        }
        entries += loadForTitleId(context, normalizedId).map { it.copy(folder = "MMJ 3DS ($normalizedId)") }
        if (entries.isEmpty()) return false

        insertParsedCheats(database, normalizedId, gameTitle.ifBlank { "3DS ($normalizedId)" }, entries, existing?.id)
        return true
    }

    suspend fun populateAllBundled3dsCheats(
        context: Context,
        database: MelonDatabase,
    ): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val tidRegex = Regex("""^([0-9A-Fa-f]{16})""")
        val titleIds = linkedSetOf<String>()
        runCatching {
            context.assets.list("cheats").orEmpty().forEach { name ->
                if (name.endsWith(".txt", ignoreCase = true)) {
                    tidRegex.find(name)?.groupValues?.getOrNull(1)?.uppercase()?.let(titleIds::add)
                }
            }
        }
        val cheatsDir = File(android.os.Environment.getExternalStorageDirectory(), "STORM 3DS/cheats")
        if (cheatsDir.isDirectory) {
            cheatsDir.listFiles { f -> f.isFile && f.name.endsWith(".txt", ignoreCase = true) }?.forEach { file ->
                tidRegex.find(file.name)?.groupValues?.getOrNull(1)?.uppercase()?.let(titleIds::add)
            }
        }
        var importedGames = 0
        for (tid in titleIds) {
            val existingTitle = database.gameDao().findGameByCode(tid)?.name.orEmpty().ifBlank { "3DS ($tid)" }
            if (populate3dsCheatsIfEmpty(context, database, tid, existingTitle)) {
                importedGames++
            }
        }
        importedGames
    }

    suspend fun insertParsedCheats(
        database: MelonDatabase,
        titleIdHex: String,
        gameTitle: String,
        entries: List<ParsedCheatEntry>,
        existingGameId: Long? = null,
    ): Int {
        if (entries.isEmpty()) return 0
        val dbList = database.cheatDatabaseDao().getCheatDatabases()
        val dbId = dbList.firstOrNull { it.id != null }?.id
            ?: database.cheatDatabaseDao().insertCheatDatabase(CheatDatabaseEntity(id = null, name = "Gateway 3DS Cheats"))
        val gameId = existingGameId ?: database.gameDao().insertGame(
            GameEntity(
                id = null,
                name = gameTitle.ifBlank { "3DS ($titleIdHex)" },
                gameCode = titleIdHex,
                gameChecksum = titleIdHex.takeLast(8),
            )
        )
        if (gameId <= 0L) return 0
        val existingFolders = database.cheatFolderDao().getFoldersForGame(gameId).associateBy { it.name }
        var inserted = 0
        for ((folderName, groupEntries) in entries.groupBy { it.folder.ifBlank { "Gateway 3DS" } }) {
            val folderId = existingFolders[folderName]?.id ?: database.cheatFolderDao().insertCheatFolder(
                CheatFolderEntity(id = null, gameId = gameId, name = folderName)
            )
            val existingNames = database.cheatDao().getFolderCheatsList(folderId).map { it.name }.toSet()
            val toInsert = groupEntries
                .filter { it.name !in existingNames }
                .map { entry ->
                    CheatEntity(
                        id = null,
                        cheatDatabaseId = dbId,
                        cheatFolderId = folderId,
                        name = entry.name,
                        description = entry.notes,
                        code = entry.code,
                        enabled = false,
                    )
                }
            if (toInsert.isNotEmpty()) {
                database.cheatDao().insertCheats(toInsert)
                inserted += toInsert.size
            }
        }
        return inserted
    }
}
