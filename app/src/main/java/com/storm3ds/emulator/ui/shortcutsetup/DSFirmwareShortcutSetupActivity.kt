package com.storm3ds.emulator.ui.shortcutsetup

import com.storm3ds.emulator.domain.model.ConsoleType

class DSFirmwareShortcutSetupActivity : FirmwareShortcutSetupActivity() {
    override fun getConsoleType(): ConsoleType {
        return ConsoleType.DS
    }
}