package com.storm3ds.emulator.ui.common.rom

import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.domain.model.emulator.validation.RomLaunchPreconditionCheckResult

sealed class RomValidationResult {
    data class Success(val rom: Rom) : RomValidationResult()
    data class PreconditionsCheckFailed(val preconditionsCheckResult: RomLaunchPreconditionCheckResult) : RomValidationResult()
}