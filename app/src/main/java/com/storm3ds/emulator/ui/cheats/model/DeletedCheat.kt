package com.storm3ds.emulator.ui.cheats.model

import com.storm3ds.emulator.domain.model.Cheat
import com.storm3ds.emulator.domain.model.CheatFolder

data class DeletedCheat(val cheat: Cheat, val folder: CheatFolder)