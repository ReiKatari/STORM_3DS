package com.storm3ds.emulator.domain.model.emulator

import com.storm3ds.emulator.MelonEmulator

sealed class FirmwareLaunchResult {
    data class LaunchFailed(val reason: MelonEmulator.FirmwareLoadResult) : FirmwareLaunchResult()
    object LaunchSuccessful : FirmwareLaunchResult()
}