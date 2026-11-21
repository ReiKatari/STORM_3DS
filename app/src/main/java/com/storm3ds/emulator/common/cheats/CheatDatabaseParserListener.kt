package com.storm3ds.emulator.common.cheats

import com.storm3ds.emulator.domain.model.CheatDatabase
import com.storm3ds.emulator.domain.model.Game

interface CheatDatabaseParserListener {
    fun onDatabaseParseStart(databaseName: String): CheatDatabase
    fun onGameParseStart(gameName: String)
    fun onGameParsed(game: Game)
    fun onParseComplete()
}