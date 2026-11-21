package com.storm3ds.emulator.domain.model.emulator.validation

import com.storm3ds.emulator.domain.model.ConfigurationDirResult
import com.storm3ds.emulator.domain.model.ConsoleType
import com.storm3ds.emulator.domain.model.VideoRenderer

sealed class FirmwareLaunchPreconditionCheckResult {
    data class Success(val consoleType: ConsoleType) : FirmwareLaunchPreconditionCheckResult()
    data class BiosConfigurationIncorrect(val configurationDirectoryResult: ConfigurationDirResult) : FirmwareLaunchPreconditionCheckResult()
    data class RendererUnsupported(val renderer: VideoRenderer) : FirmwareLaunchPreconditionCheckResult()
    data class RendererInitFailed(val renderer: VideoRenderer) : FirmwareLaunchPreconditionCheckResult()
}
