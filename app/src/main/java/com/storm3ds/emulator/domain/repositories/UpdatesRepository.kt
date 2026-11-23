package com.storm3ds.emulator.domain.repositories

import com.storm3ds.emulator.domain.model.appupdate.AppUpdate

interface UpdatesRepository {
    suspend fun checkNewUpdate(ignorePreferences: Boolean = false): Result<AppUpdate?>
    suspend fun checkNewUpdateManually(): Result<AppUpdate?> = checkNewUpdate(ignorePreferences = true)
    fun skipUpdate(update: AppUpdate)
    fun notifyUpdateDownloaded(update: AppUpdate)
}
