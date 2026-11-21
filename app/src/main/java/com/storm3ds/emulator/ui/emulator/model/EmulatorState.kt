package com.storm3ds.emulator.ui.emulator.model

import com.storm3ds.emulator.MelonEmulator
import com.storm3ds.emulator.domain.model.ConsoleType
import com.storm3ds.emulator.domain.model.rom.Rom

sealed class EmulatorState {
    data object Uninitialized : EmulatorState()
    data class ValidatingRom(val rom: Rom) : EmulatorState()
    data class LoadingRom(
        val vulkanCompileProgress: VulkanCompileProgress? = null,
        val retroAchievementsLoadStage: RetroAchievementsLoadStage? = null,
    ) : EmulatorState()
    data class ValidatingFirmware(val consoleType: ConsoleType) : EmulatorState()
    data class LoadingFirmware(
        val consoleType: ConsoleType = ConsoleType.DS,
        val vulkanCompileProgress: VulkanCompileProgress? = null,
    ) : EmulatorState()
    data class RunningRom(val rom: Rom) : EmulatorState()
    data class RunningFirmware(val console: ConsoleType) : EmulatorState()
    data object RomLoadError : EmulatorState()
    data class FirmwareLoadError(val reason: MelonEmulator.FirmwareLoadResult) : EmulatorState()
    data class RomNotFoundError(val romPath: String) : EmulatorState()

    fun isRunning() = this is RunningRom || this is RunningFirmware
}

data class VulkanCompileProgress(
    val stageId: Int,
    val current: Int,
    val total: Int,
)

enum class RetroAchievementsLoadStage {
    FETCHING_LATEST_DATA,
}
