package com.storm3ds.emulator.common.romprocessors

import android.content.Context
import android.net.Uri
import com.storm3ds.emulator.common.uridelegates.UriHandler
import com.storm3ds.emulator.domain.model.SizeUnit
import com.storm3ds.emulator.impl.NdsRomCache
import com.storm3ds.emulator.utils.FileUtils
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class ZipRomFileProcessor(
    context: Context,
    uriHandler: UriHandler,
    ndsRomCache: NdsRomCache,
) : CompressedRomFileProcessor(context, uriHandler, ndsRomCache) {

    private class ZipFileEntryInputStream(
        inputStream: InputStream,
        private val zipFile: ZipFile,
    ) : FilterInputStream(inputStream) {
        override fun close() {
            try {
                super.close()
            } finally {
                runCatching { zipFile.close() }
            }
        }
    }

    override fun openBestEntryForUri(romUri: Uri): RomFileStream? {
        val directFile = FileUtils.resolveDirectFileFromExternalStorageUri(romUri)
            ?: romUri.takeIf { it.scheme == "file" || it.scheme == null }?.path?.let(::File)
        if (directFile == null || !directFile.isFile || !directFile.canRead()) {
            return null
        }

        var zipFile: ZipFile? = null
        return try {
            zipFile = ZipFile(directFile)
            val bestEntry = zipFile.entries().asSequence()
                .filter { !it.isDirectory && isSupportedRomFile(it.name) }
                .maxWithOrNull(
                    compareBy<ZipEntry> { romEntryPriority(it.name) }
                        .thenBy { it.size.coerceAtLeast(0L) }
                )
            if (bestEntry == null) {
                runCatching { zipFile.close() }
                return null
            }
            val ext = bestEntry.name.substringAfterLast('.', "3ds").lowercase()
            val entryStream = ZipFileEntryInputStream(
                zipFile.getInputStream(bestEntry).buffered(),
                zipFile,
            )
            RomFileStream(entryStream, SizeUnit.Bytes(bestEntry.size.coerceAtLeast(0L)), ext)
        } catch (_: Throwable) {
            runCatching { zipFile?.close() }
            null
        }
    }

    override fun getNdsEntryStreamInFileStream(fileStream: InputStream): RomFileStream? {
        val zipStream = ZipInputStream(fileStream)
        return getNdsEntryInZipStream(zipStream)?.let { entry ->
            val ext = entry.name.substringAfterLast('.', "3ds").lowercase()
            RomFileStream(zipStream.buffered(), SizeUnit.Bytes(entry.size.coerceAtLeast(0L)), ext)
        }
    }

    private fun getNdsEntryInZipStream(inputStream: ZipInputStream): ZipEntry? {
        while (true) {
            val nextEntry = inputStream.nextEntry ?: break
            if (!nextEntry.isDirectory && isSupportedRomFile(nextEntry.name)) {
                return nextEntry
            }
        }
        return null
    }
}
