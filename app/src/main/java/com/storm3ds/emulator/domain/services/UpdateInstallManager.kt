package com.storm3ds.emulator.domain.services

import kotlinx.coroutines.flow.Flow
import com.storm3ds.emulator.domain.model.DownloadProgress
import com.storm3ds.emulator.domain.model.appupdate.AppUpdate

interface UpdateInstallManager {
    fun downloadAndInstallUpdate(update: AppUpdate): Flow<DownloadProgress>
}