package com.storm3ds.emulator.common.romprocessors

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.storm3ds.emulator.MelonEmulator
import com.storm3ds.emulator.common.uridelegates.UriHandler
import com.storm3ds.emulator.domain.model.RomInfo
import com.storm3ds.emulator.domain.model.RomMetadata
import com.storm3ds.emulator.domain.model.SizeUnit
import com.storm3ds.emulator.domain.model.ThreeDS
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.domain.model.rom.config.RomConfig
import com.storm3ds.emulator.extensions.isBlank
import com.storm3ds.emulator.extensions.nameWithoutExtension
import com.storm3ds.emulator.impl.NdsRomCache
import com.storm3ds.emulator.utils.FileUtils
import com.storm3ds.emulator.utils.RomProcessor
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

abstract class CompressedRomFileProcessor(
    private val context: Context,
    private val uriHandler: UriHandler,
    private val ndsRomCache: NdsRomCache,
) : RomFileProcessor {

    companion object {
        private val PLAYABLE_ROM_EXTENSIONS = setOf(
            "3ds", "cci", "zcci", "bcci", "cxi", "zcxi", "bcxi", "app", "3dsx", "z3dsx",
        )
        private val INSTALLABLE_CIA_EXTENSIONS = setOf("cia", "zcia", "bcia")
        private val OTHER_SUPPORTED_EXTENSIONS = setOf("elf", "axf", "artic")

        internal fun romEntryPriority(fileName: String): Int {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                in PLAYABLE_ROM_EXTENSIONS -> 3
                in INSTALLABLE_CIA_EXTENSIONS -> 2
                in OTHER_SUPPORTED_EXTENSIONS -> 1
                else -> 0
            }
        }
    }

    private sealed class RomExtractionException(message: String) : Exception(message)
    private class CouldNotOpenCompressedFileException : RomExtractionException("Failed to open compressed file for extraction")
    private class CouldNotFind3dsRomException : RomExtractionException("Failed to find a Nintendo 3DS ROM inside archive")
    private class CouldNotFindExtractedFileException : RomExtractionException("Failed to find extracted 3DS ROM file")

    protected open fun openBestEntryForUri(romUri: Uri): RomFileStream? = null

    protected open fun openRomUriInputStream(uri: Uri): InputStream? {
        val directFile = FileUtils.resolveDirectFileFromExternalStorageUri(uri)
        if (directFile != null && directFile.exists() && directFile.canRead()) {
            runCatching { return BufferedInputStream(FileInputStream(directFile), 131072) }
        }
        return runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }

    override fun getRomFromUri(romUri: Uri, parentUri: Uri?): Rom? {
        return try {
            val romDocument = uriHandler.getUriDocument(romUri)
            val fallbackName = romDocument?.nameWithoutExtension?.takeUnless { it.isBlank() }
                ?: romUri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?: "Nintendo 3DS Game"
            val fileName = romDocument?.name ?: fallbackName
            Rom(
                name = fallbackName,
                developerName = "",
                fileName = fileName,
                uri = romUri,
                parentTreeUri = parentUri,
                config = RomConfig.default(),
                lastPlayed = null,
                isDsiWareTitle = false,
                isDsiEnhanced = false,
                retroAchievementsHash = "",
                gameCode = "",
                installedDsiWareTitleId = null,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getRomIcon(rom: Rom): Bitmap? = null

    override fun getRomInfo(rom: Rom): RomInfo? {
        return RomInfo(
            gameCode = rom.gameCode,
            headerChecksum = 0u,
            gameTitle = rom.name,
            gameName = rom.name,
            isDsiWareTitle = false,
        )
    }

    fun getRomMetadata(romUri: Uri): RomMetadata? = null

    override suspend fun getRealRomUri(rom: Rom): Uri? {
        val cachedRomUri = ndsRomCache.getCachedRomFile(rom, true)
        return cachedRomUri ?: runCatching { extractRomFile(rom) }.getOrNull()
    }

    protected fun isSupportedRomFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in ThreeDS.NATIVE_3DS_EXTENSIONS
    }

    private fun CoroutineScope.cacheEntryStream(rom: Rom, entryStream: RomFileStream) {
        try {
            ndsRomCache.cacheRom(
                rom,
                object : NdsRomCache.RomExtractor {
                    override fun getExtractedRomFileSize(): SizeUnit = entryStream.romFileSize
                    override fun saveRomFile(fileStream: FileOutputStream): Boolean {
                        val buffer = ByteArray(65536)
                        return try {
                            while (coroutineContext.isActive) {
                                val read = entryStream.read(buffer)
                                if (read <= 0) break
                                fileStream.write(buffer, 0, read)
                            }
                            coroutineContext.isActive
                        } catch (_: IOException) {
                            false
                        }
                    }
                },
                extension = entryStream.entryExtension ?: "3ds",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw CouldNotFindExtractedFileException()
        }
    }

    private suspend fun extractRomFile(rom: Rom): Uri? = withContext(Dispatchers.IO) {
        MelonEmulator.isLongPreparationInProgress = true
        try {
            val directEntryStream = openBestEntryForUri(rom.uri)
            if (directEntryStream != null) {
                directEntryStream.use { entryStream ->
                    cacheEntryStream(rom, entryStream)
                    if (!coroutineContext.isActive) throw CancellationException("ROM extraction cancelled")
                    return@withContext ndsRomCache.getCachedRomFile(rom) ?: throw CouldNotFindExtractedFileException()
                }
            }
            val baseStream = openRomUriInputStream(rom.uri)
                ?: throw CouldNotOpenCompressedFileException()
            baseStream.use { stream ->
                val romFileStream = getNdsEntryStreamInFileStream(stream)
                    ?: throw CouldNotFind3dsRomException()
                romFileStream.use { entryStream ->
                    cacheEntryStream(rom, entryStream)
                    if (!coroutineContext.isActive) throw CancellationException("ROM extraction cancelled")
                    ndsRomCache.getCachedRomFile(rom) ?: throw CouldNotFindExtractedFileException()
                }
            }
        } finally {
            MelonEmulator.isLongPreparationInProgress = false
        }
    }

    abstract fun getNdsEntryStreamInFileStream(fileStream: InputStream): RomFileStream?

    class RomFileStream(
        stream: InputStream,
        val romFileSize: SizeUnit,
        val entryExtension: String? = null,
    ) : FilterInputStream(stream)
}
