package com.storm3ds.emulator.common.romprocessors

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.getSystemService
import com.storm3ds.emulator.common.uridelegates.UriHandler
import com.storm3ds.emulator.domain.model.SizeUnit
import com.storm3ds.emulator.impl.NdsRomCache
import com.storm3ds.emulator.utils.FileUtils
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream

class SevenZRomFileProcessor(private val context: Context, uriHandler: UriHandler, ndsRomCache: NdsRomCache) : CompressedRomFileProcessor(context, uriHandler, ndsRomCache) {
    companion object {
        private const val TAG = "SevenZRomProcessor"
        private const val MIN_MEMORY_LIMIT_KB = 1024L

        internal fun calculateMaxMemoryLimitKb(deviceMemoryBytes: Long, maxHeapBytes: Long): Int {
            val deviceMemoryLimitBytes = (deviceMemoryBytes * 0.1f).toLong()
            val heapMemoryLimitBytes = maxHeapBytes / 3
            val memoryLimitBytes = minOf(deviceMemoryLimitBytes, heapMemoryLimitBytes)
                .coerceAtLeast(MIN_MEMORY_LIMIT_KB * 1024)

            return (memoryLimitBytes / 1024)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
    }

    override fun openRomUriInputStream(uri: Uri): InputStream? {
        val directFile = FileUtils.resolveDirectFileFromExternalStorageUri(uri)
        if (directFile != null && directFile.exists() && directFile.canRead()) {
            runCatching { return FileInputStream(directFile) }
        }
        if (uri.scheme == "file" || uri.scheme == null) {
            uri.path?.let { path ->
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    runCatching { return FileInputStream(file) }
                }
            }
        }
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.let { pfd ->
                return ParcelFileDescriptor.AutoCloseInputStream(pfd)
            }
        }
        return runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }

    override fun getNdsEntryStreamInFileStream(fileStream: InputStream): RomFileStream? {
        val maxMemoryLimitKb = calculateMaxMemoryLimitKb(
            deviceMemoryBytes = getDeviceMemoryBytes(),
            maxHeapBytes = Runtime.getRuntime().maxMemory()
        )

        var sevenZFile: SevenZFile? = null
        var tempArchiveFile: File? = null
        var returned = false
        return try {
            sevenZFile = if (fileStream is FileInputStream) {
                SevenZFile.Builder()
                    .setMaxMemoryLimitKb(maxMemoryLimitKb)
                    .setSeekableByteChannel(fileStream.channel)
                    .get()
            } else {
                val tmp = File.createTempFile("storm3ds_7z_", ".7z", context.cacheDir)
                tempArchiveFile = tmp
                tmp.outputStream().use { out -> fileStream.copyTo(out, 65536) }
                runCatching { fileStream.close() }
                SevenZFile.Builder()
                    .setMaxMemoryLimitKb(maxMemoryLimitKb)
                    .setFile(tmp)
                    .get()
            }

            val ndsEntry = getNdsEntryInFile(sevenZFile) ?: return null
            val ext = ndsEntry.name.substringAfterLast('.', "3ds").lowercase()
            val entryStream = SevenZEntryInputStream(
                sevenZFile.getInputStream(ndsEntry),
                sevenZFile,
                fileStream,
                tempArchiveFile,
            )
            val romFileStream = RomFileStream(entryStream, SizeUnit.Bytes(ndsEntry.size), ext)
            returned = true
            romFileStream
        } finally {
            if (!returned) {
                runCatching { sevenZFile?.close() }
                runCatching { fileStream.close() }
                runCatching { tempArchiveFile?.delete() }
            }
        }
    }

    private fun getDeviceMemoryBytes(): Long {
        return context.getSystemService<ActivityManager>()?.let {
            val memoryInfo = ActivityManager.MemoryInfo()
            it.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        } ?: Runtime.getRuntime().maxMemory()
    }

    private fun getNdsEntryInFile(sevenZFile: SevenZFile): SevenZArchiveEntry? {
        do {
            val nextEntry = sevenZFile.nextEntry ?: break
            if (!nextEntry.isDirectory && isSupportedRomFile(nextEntry.name)) {
                return nextEntry
            }
        } while (true)
        return null
    }

    private class SevenZEntryInputStream(
        stream: InputStream,
        private val sevenZFile: SevenZFile,
        private val fileStream: InputStream,
        private val tempArchiveFile: File? = null,
    ) : FilterInputStream(stream) {
        override fun close() {
            try {
                super.close()
            } finally {
                try {
                    sevenZFile.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to close 7z archive", e)
                } finally {
                    try {
                        fileStream.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to close 7z file stream", e)
                    } finally {
                        runCatching { tempArchiveFile?.delete() }
                    }
                }
            }
        }
    }
}
