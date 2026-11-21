package com.storm3ds.emulator.ui.common.rom.model

import com.storm3ds.emulator.domain.model.emulator.validation.FirmwareLaunchPreconditionCheckResult
import com.storm3ds.emulator.domain.model.emulator.validation.RomLaunchPreconditionCheckResult

sealed class LaunchValidationResult {
    data class Rom(val result: RomLaunchPreconditionCheckResult) : LaunchValidationResult()
    data class Firmware(val result: FirmwareLaunchPreconditionCheckResult) : LaunchValidationResult()
}