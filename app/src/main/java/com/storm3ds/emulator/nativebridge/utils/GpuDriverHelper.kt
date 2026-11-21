package com.storm3ds.emulator.nativebridge.utils

import android.content.Context
import android.net.Uri
import com.storm3ds.emulator.nativebridge.NativeBridgeHost
import com.storm3ds.emulator.nativebridge.NativeLibrary
import java.io.File

object GpuDriverHelper {
    var driverInstallationPath: String? = null
    private var fileRedirectionPath: String? = null
    private var hookLibPath: String? = null

    fun initializeDriverParameters(
        customDriverDir: String? = null,
        customDriverName: String? = null,
    ) {
        val ctx: Context = NativeBridgeHost.appContext
        fileRedirectionPath = File(ctx.filesDir, "gpu/vk_file_redirect/").apply { mkdirs() }.canonicalPath + "/"
        driverInstallationPath = customDriverDir ?: File(ctx.filesDir, "gpu_driver/").apply { mkdirs() }.canonicalPath + "/"
        hookLibPath = ctx.applicationInfo.nativeLibraryDir + "/"

        runCatching {
            NativeLibrary.initializeGpuDriver(
                hookLibPath,
                driverInstallationPath,
                customDriverName,
                fileRedirectionPath
            )
        }
    }

    fun installDefaultDriver() {
        driverInstallationPath?.let { File(it).deleteRecursively() }
        initializeDriverParameters(null, null)
    }

    fun getDriverMetadataFromZip(context: Context, uri: Uri): GpuDriverMetadata? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                java.util.zip.ZipInputStream(stream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.equals("meta.json", ignoreCase = true)) {
                            return GpuDriverMetadata(zis, entry.size.coerceAtLeast(4096L))
                        }
                        entry = zis.nextEntry
                    }
                    null
                }
            }
        }.getOrNull()
    }
}
