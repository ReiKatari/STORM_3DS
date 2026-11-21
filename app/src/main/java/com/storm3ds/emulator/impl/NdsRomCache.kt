package com.storm3ds.emulator.impl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import com.storm3ds.emulator.domain.model.SizeUnit
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.domain.repositories.SettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.util.Date

class NdsRomCache(private val context: Context, private val settingsRepository: SettingsRepository) {
    companion object {
        private const val ROMS_CACHE_DIR = "extracted_3ds"
        private const val TEMP_FILE_NAME = "temp"
    }

    private class CouldNotCreateRomCacheDirectoryException : Exception("Failed to create ROM cache directory")

    private val cacheModifiedSubject = Channel<Unit>(Channel.CONFLATED)

    fun getCacheSizeFlow(): Flow<SizeUnit> {
        return cacheModifiedSubject.receiveAsFlow()
            .onStart { emit(Unit) }
            .map { calculateCacheSize() }
    }

    private fun getRomCacheDir(): File? {
        val extRoot = runCatching { android.os.Environment.getExternalStorageDirectory() }.getOrNull()
        val extCandidate = if (extRoot != null) File(extRoot, "STORM 3DS/cache/$ROMS_CACHE_DIR") else null
        if (extCandidate != null && (extCandidate.isDirectory || extCandidate.mkdirs())) {
            val probe = runCatching {
                val f = File.createTempFile(".p_", null, extCandidate)
                f.delete()
                true
            }.getOrDefault(false)
            if (probe) return extCandidate
        }
        return context.cacheDir.let { File(it, ROMS_CACHE_DIR) }
    }

    fun clearCache(): Boolean {
        val romCacheDir = getRomCacheDir() ?: return false
        val result = romCacheDir.deleteRecursively()
        cacheModifiedSubject.trySend(Unit)
        return result
    }

    fun getCachedRomFile(rom: Rom, forUse: Boolean = false): Uri? {
        val romHash = rom.uri.hashCode().toString()
        val romCacheDir = getRomCacheDir()
        if (romCacheDir == null || !romCacheDir.isDirectory) {
            return null
        }

        val matching = romCacheDir.listFiles()?.firstOrNull { file ->
            file.isFile && (file.name == romHash || file.name.startsWith("$romHash."))
        }
        return if (matching != null && matching.length() > 0L) {
            if (forUse) {
                matching.setLastModified(Date().time)
            }
            DocumentFile.fromFile(matching).uri
        } else {
            if (matching != null && matching.length() == 0L) {
                matching.delete()
            }
            null
        }
    }

    fun cacheRom(rom: Rom, romExtractor: RomExtractor, extension: String? = null) {
        val romCacheDir = getRomCacheDir()
        if (romCacheDir == null || !(romCacheDir.isDirectory || romCacheDir.mkdirs())) {
            throw CouldNotCreateRomCacheDirectoryException()
        }

        val romHash = rom.uri.hashCode().toString()
        val cleanExt = extension?.lowercase()?.trim()?.removePrefix(".")?.takeIf { it.isNotEmpty() }
        val targetName = if (cleanExt != null) "$romHash.$cleanExt" else romHash
        val tempCachedFile = File(romCacheDir, "${TEMP_FILE_NAME}_${romHash}_${System.nanoTime()}")

        try {
            val romFileSize = romExtractor.getExtractedRomFileSize()
            freeCacheSpaceIfRequired(romFileSize)

            var success = false
            tempCachedFile.outputStream().use {
                success = romExtractor.saveRomFile(it)
            }
            if (success && tempCachedFile.isFile && tempCachedFile.length() > 0L) {
                val cachedFile = File(romCacheDir, targetName)
                if (cachedFile.exists()) {
                    cachedFile.delete()
                }
                if (!tempCachedFile.renameTo(cachedFile)) {
                    tempCachedFile.copyTo(cachedFile, overwrite = true)
                    tempCachedFile.delete()
                }
                cacheModifiedSubject.trySend(Unit)
            } else {
                tempCachedFile.delete()
            }
        } catch (e: Exception) {
            tempCachedFile.delete()
            throw e
        }
    }

    private fun calculateCacheSize(): SizeUnit {
        val romCacheDir = getRomCacheDir()
        val cacheSize = romCacheDir?.listFiles()?.sumOf { file: File -> file.length() } ?: 0L
        return SizeUnit.Bytes(cacheSize)
    }

    private fun freeCacheSpaceIfRequired(requiredSize: SizeUnit) {
        val currentCacheSize = calculateCacheSize()
        val maxCacheSize = settingsRepository.getRomCacheMaxSize()
        val requiredCacheSize = currentCacheSize + requiredSize
        if (requiredCacheSize > maxCacheSize) {
            freeCacheSpace(requiredCacheSize - maxCacheSize)
        }
    }

    private fun freeCacheSpace(sizeToFree: SizeUnit) {
        val romCacheDir = getRomCacheDir()
        val romFilesByCreationDate = romCacheDir?.listFiles()?.sortedBy { file: File -> file.lastModified() } ?: return
        var freedBytes = SizeUnit.Bytes(0)
        for (file in romFilesByCreationDate) {
            freedBytes += file.length()
            file.delete()
            if (freedBytes >= sizeToFree) break
        }
    }

    interface RomExtractor {
        fun getExtractedRomFileSize(): SizeUnit
        fun saveRomFile(fileStream: FileOutputStream): Boolean
    }
}
