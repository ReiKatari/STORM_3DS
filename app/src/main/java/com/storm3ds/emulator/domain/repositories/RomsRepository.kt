package com.storm3ds.emulator.domain.repositories

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.domain.model.rom.config.RomConfig
import com.storm3ds.emulator.domain.model.rom.RomDirectoryScanStatus
import com.storm3ds.emulator.domain.model.RomScanningStatus
import java.util.*
import kotlin.time.Duration

interface RomsRepository {
    fun getRoms(): Flow<List<Rom>>
    fun getRomScanningStatus(): StateFlow<RomScanningStatus>
    fun observeRomDirectoryScanStatuses(): Flow<List<RomDirectoryScanStatus>>
    suspend fun getRomAtPath(path: String): Rom?
    suspend fun getRomAtUri(uri: Uri): Rom?

    fun updateRomConfig(rom: Rom, romConfig: RomConfig)
    fun setRomLastPlayed(rom: Rom, lastPlayed: Date)
    fun addRomPlayTime(rom: Rom, playTime: Duration)
    fun setRomFavorite(rom: Rom, favorite: Boolean)
    fun rescanRoms()
    fun invalidateRoms()
}
