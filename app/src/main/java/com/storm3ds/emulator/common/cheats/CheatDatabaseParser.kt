package com.storm3ds.emulator.common.cheats

interface CheatDatabaseParser {
    fun parseCheatDatabase(databaseStream: ProgressTrackerInputStream, parseListener: CheatDatabaseParserListener)
}