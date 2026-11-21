package com.storm3ds.emulator.domain.model.emulator

import com.storm3ds.emulator.MelonEmulator

sealed class RomLaunchResult {
    data object LaunchFailedRomNotFound : RomLaunchResult()
    data object LaunchFailedRomNotSupported : RomLaunchResult()
    data class LaunchFailedSramProblem(val reason: Exception) : RomLaunchResult()
    data class LaunchFailed(val reason: MelonEmulator.LoadResult) : RomLaunchResult()
    data class LaunchSuccessful(val isGbaLoadSuccessful: Boolean) : RomLaunchResult()
}
