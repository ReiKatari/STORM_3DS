package com.storm3ds.emulator.common.cheats

import android.content.Context
import com.storm3ds.emulator.domain.model.Cheat

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
}
