package com.storm3ds.emulator.common.romprocessors

import android.content.Context
import com.storm3ds.emulator.common.uridelegates.UriHandler
import com.storm3ds.emulator.domain.model.SizeUnit
import com.storm3ds.emulator.impl.NdsRomCache
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipRomFileProcessor(
    context: Context,
    uriHandler: UriHandler,
    ndsRomCache: NdsRomCache,
) : CompressedRomFileProcessor(context, uriHandler, ndsRomCache) {

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
