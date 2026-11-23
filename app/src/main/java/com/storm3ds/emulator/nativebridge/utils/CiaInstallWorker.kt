package com.storm3ds.emulator.nativebridge.utils

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import androidx.documentfile.provider.DocumentFile
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.storm3ds.emulator.nativebridge.NativeLibrary
import java.io.File
import java.util.UUID

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
            var tempFile: File? = null
            val resolvedPath = if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                val nativeCandidate = NativeLibrary.resolveNativeOrContentPath(uri)
                val rawFile = File(nativeCandidate.removePrefix("!"))
                if (!nativeCandidate.startsWith("content://") && rawFile.isFile && rawFile.canRead()) {
                    nativeCandidate
                } else {
                    val docName = runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull().orEmpty()
                    val suffix = when {
                        docName.endsWith(".zcia", ignoreCase = true) -> ".zcia"
                        docName.endsWith(".bcia", ignoreCase = true) -> ".bcia"
                        else -> ".cia"
                    }
                    val tmp = File.createTempFile("storm3ds_cia_", suffix, context.cacheDir)
                    tempFile = tmp
                    val copied = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { out -> input.copyTo(out, 65536) }
                        } != null
                    }.getOrDefault(false)
                    if (!copied || !tmp.isFile || tmp.length() <= 0L) {
                        runCatching { tmp.delete() }
                        return Result.failure(
                            workDataOf(
                                KEY_ERROR_STATUS to NativeLibrary.InstallStatus.ErrorFileNotFound.name,
                                KEY_FAILED_PATH to path,
                            )
                        )
                    }
                    tmp.absolutePath
                }
            } else {
                path
            }
            val status = try {
                installCIA(resolvedPath)
            } finally {
                runCatching { tempFile?.delete() }
            }
            if (status != NativeLibrary.InstallStatus.Success) {
                return Result.failure(
                    workDataOf(
                        KEY_ERROR_STATUS to status.name,
                        KEY_FAILED_PATH to path,
                    )
                )
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
        const val KEY_ERROR_STATUS = "cia_error_status"
        const val KEY_FAILED_PATH = "cia_failed_path"
        @Volatile
        var progressListener: ((Int, Int) -> Unit)? = null

        fun enqueueInstall(context: Context, paths: Array<String>) {
            enqueueInstallAndReturnId(context, paths)
        }

        fun enqueueInstallAndReturnId(context: Context, paths: Array<String>): UUID {
            val req = OneTimeWorkRequestBuilder<CiaInstallWorker>()
                .setInputData(workDataOf(KEY_FILE_PATHS to paths))
                .build()
            WorkManager.getInstance(context).enqueue(req)
            return req.id
        }
    }
}
