package com.storm3ds.emulator.ui.emulator.firmware

import com.storm3ds.emulator.R
import com.storm3ds.emulator.ui.emulator.PauseMenuOption

enum class FirmwarePauseMenuOption(override val textResource: Int) : PauseMenuOption {
    SETTINGS(R.string.settings),
    SCREEN_LAYOUT(R.string.screen_layout),
    RESET(R.string.reset),
    EXIT(R.string.exit)
}