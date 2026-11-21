package com.storm3ds.emulator.ui.emulator.model

import com.storm3ds.emulator.ui.emulator.PauseMenuOption

data class PauseMenu(
    val options: List<PauseMenuOption>,
    val labelOverrides: Map<PauseMenuOption, String> = emptyMap(),
) {
    fun labelOverride(option: PauseMenuOption): String? = labelOverrides[option]
}
