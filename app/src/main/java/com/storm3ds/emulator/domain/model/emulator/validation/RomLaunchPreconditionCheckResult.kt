package com.storm3ds.emulator.domain.model.emulator.validation

import com.storm3ds.emulator.domain.model.ConfigurationDirResult
import com.storm3ds.emulator.domain.model.VideoRenderer
import com.storm3ds.emulator.domain.model.rom.Rom

sealed class RomLaunchPreconditionCheckResult {
    data class Success(val rom: Rom) : RomLaunchPreconditionCheckResult()
    data class RomEncrypted(val rom: Rom) : RomLaunchPreconditionCheckResult()
    data class DSiWareTitleValidationFailed(val reason: Reason) : RomLaunchPreconditionCheckResult() {
        sealed class Reason {
            object NandError : Reason()
            object RomParseError : Reason()
            object TitleNotInstalled : Reason()
        }
    }
    data class BiosConfigurationIncorrect(val configurationDirectoryResult: ConfigurationDirResult) : RomLaunchPreconditionCheckResult()
    data class RendererUnsupported(val renderer: VideoRenderer) : RomLaunchPreconditionCheckResult()
    data class RendererInitFailed(val renderer: VideoRenderer) : RomLaunchPreconditionCheckResult()
}
