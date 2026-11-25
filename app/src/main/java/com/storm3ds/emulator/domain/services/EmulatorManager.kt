package com.storm3ds.emulator.domain.services

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import com.storm3ds.emulator.domain.model.Cheat
import com.storm3ds.emulator.domain.model.ConsoleType
import com.storm3ds.emulator.domain.model.emulator.EmulatorEvent
import com.storm3ds.emulator.domain.model.emulator.FirmwareLaunchResult
import com.storm3ds.emulator.domain.model.emulator.RomLaunchResult
import com.storm3ds.emulator.domain.model.retroachievements.GameAchievementData
import com.storm3ds.emulator.domain.model.retroachievements.RAEvent
import com.storm3ds.emulator.domain.model.retroachievements.RaNativePendingRetryResult
import com.storm3ds.emulator.domain.model.retroachievements.RARuntimeBridgeConfig
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.ui.emulator.rewind.model.RewindSaveState
import com.storm3ds.emulator.ui.emulator.rewind.model.RewindWindow

interface EmulatorManager {

    val emulatorEvents: Flow<EmulatorEvent>

    suspend fun loadRom(rom: Rom, cheats: List<Cheat>): RomLaunchResult

    suspend fun loadFirmware(consoleType: ConsoleType): FirmwareLaunchResult

    suspend fun updateRomEmulatorConfiguration(rom: Rom)

    suspend fun updateFirmwareEmulatorConfiguration(consoleType: ConsoleType)

    suspend fun getRewindWindow(): RewindWindow

    fun getFps(): Float

    fun pollPerfStatsSnapshot(): com.storm3ds.emulator.PerfStatsSnapshot = com.storm3ds.emulator.PerfStatsSnapshot()

    suspend fun pauseEmulator()

    suspend fun resumeEmulator()

    fun isEmulatorPaused(): Boolean = false

    suspend fun debugStepFrame(): Boolean

    suspend fun resetEmulator()

    suspend fun updateCheats(cheats: List<Cheat>)
    suspend fun setupRetroAchievements(achievementData: GameAchievementData, runtimeConfig: RARuntimeBridgeConfig?)
    suspend fun retryPendingRetroAchievementsSubmissions(
        expectedNativeSubmissionIds: List<Long>,
    ): RaNativePendingRetryResult
    suspend fun refreshPendingRetroAchievementsSubmissions(): Long
    suspend fun discardPendingRetroAchievementsSubmissions(
        expectedNativeSubmissionIds: List<Long>,
    ): Int
    suspend fun setRetroAchievementsSubmissionTransportSuspended(suspended: Boolean)
    fun unloadRetroAchievementsData()

    suspend fun loadRewindState(rewindSaveState: RewindSaveState): Boolean

    suspend fun saveState(saveStateFileUri: Uri): Boolean

    suspend fun loadState(saveStateFileUri: Uri): Boolean

    suspend fun takeScreenshot(): Boolean

    fun stopEmulator()

    fun cleanEmulator()

    fun observeRetroAchievementEvents(): Flow<RAEvent>
}
