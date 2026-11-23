package com.storm3ds.emulator.common.romprocessors

import android.content.Context
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import com.storm3ds.emulator.common.uridelegates.UriHandler
import com.storm3ds.emulator.domain.model.SizeUnit
import com.storm3ds.emulator.impl.NdsRomCache
import java.io.FilterInputStream
import java.io.InputStream

class RarRomFileProcessor(
    context: Context,
    uriHandler: UriHandler,
    ndsRomCache: NdsRomCache,
) : CompressedRomFileProcessor(context, uriHandler, ndsRomCache) {

    private class RarEntryInputStream(
        inputStream: InputStream,
        private val archive: Archive,
    ) : FilterInputStream(inputStream) {
        override fun close() {
            try {
                super.close()
            } finally {
                runCatching { archive.close() }
            }
        }
    }

    override fun getNdsEntryStreamInFileStream(fileStream: InputStream): RomFileStream? {
        var archive: Archive? = null
        return try {
            archive = Archive(fileStream)
            val header = getNdsEntryInRar(archive)
            if (header == null) {
                runCatching { archive.close() }
                null
            } else {
                val ext = (header.fileName ?: "").substringAfterLast('.', "3ds").lowercase()
                val entryStream = RarEntryInputStream(archive.getInputStream(header).buffered(), archive)
                RomFileStream(entryStream, SizeUnit.Bytes(header.fullUnpackSize), ext)
            }
        } catch (e: Throwable) {
            runCatching { archive?.close() }
            e.printStackTrace()
            null
        }
    }

    private fun getNdsEntryInRar(archive: Archive): FileHeader? {
        return archive.fileHeaders
            ?.filter { !it.isDirectory && isSupportedRomFile(it.fileName ?: "") }
            ?.maxWithOrNull(
                compareBy<FileHeader> { romEntryPriority(it.fileName ?: "") }
                    .thenBy { it.fullUnpackSize }
            )
    }
}
