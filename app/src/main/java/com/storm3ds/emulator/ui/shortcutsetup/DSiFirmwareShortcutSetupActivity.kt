package com.storm3ds.emulator.ui.shortcutsetup

import com.storm3ds.emulator.domain.model.ConsoleType

class DSiFirmwareShortcutSetupActivity : FirmwareShortcutSetupActivity() {
    override fun getConsoleType(): ConsoleType {
        return ConsoleType.DSi
    }
}