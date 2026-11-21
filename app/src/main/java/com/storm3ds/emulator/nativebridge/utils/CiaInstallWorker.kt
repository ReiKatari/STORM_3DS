package com.storm3ds.emulator.nativebridge.utils

import android.content.Context
import androidx.annotation.Keep
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.storm3ds.emulator.nativebridge.NativeLibrary

@Keep
class CiaInstallWorker(val context: Context, params: WorkerParameters) : Worker(context, params) {
    @Volatile
    var lastProgress: Int = 0
    @Volatile
    var lastMax: Int = 100

    override fun doWork(): Result {
        com.storm3ds.emulator.NativeCoreLoader.load()
        NativeLibrary.setUserDirectory(com.storm3ds.emulator.nativebridge.NativeBridgeHost.resolveUserDirectoryPath())
        val uris = inputData.getStringArray(KEY_FILE_PATHS) ?: return Result.failure()
        for (path in uris) {
            val status = installCIA(path)
            if (status != NativeLibrary.InstallStatus.Success) {
                return Result.failure()
            }
        }
        return Result.success()
    }

    @Keep
    fun setProgressCallback(progress: Int, max: Int) {
        lastProgress = progress
        lastMax = max.coerceAtLeast(1)
        progressListener?.invoke(progress, lastMax)
    }

    external fun installCIA(path: String): NativeLibrary.InstallStatus

    companion object {
        const val KEY_FILE_PATHS = "cia_file_paths"
        @Volatile
        var progressListener: ((Int, Int) -> Unit)? = null

        fun enqueueInstall(context: Context, paths: Array<String>) {
            val req = OneTimeWorkRequestBuilder<CiaInstallWorker>()
                .setInputData(workDataOf(KEY_FILE_PATHS to paths))
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
