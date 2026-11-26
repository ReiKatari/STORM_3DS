package com.storm3ds.emulator.common.romprocessors

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.storm3ds.emulator.NativeCoreLoader
import com.storm3ds.emulator.common.uridelegates.UriHandler
import com.storm3ds.emulator.domain.model.RomInfo
import com.storm3ds.emulator.domain.model.RomMetadata
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.domain.model.rom.config.RomConfig
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.nativebridge.model.GameInfo
import java.io.File
import java.security.MessageDigest

class ThreeDsRomFileProcessor(
    private val context: Context,
    private val uriHandler: UriHandler,
) : RomFileProcessor {

    companion object {
        private const val HASH_SAMPLE_BYTES = 16 * 1024

        fun compute3dsHashFromBytes(
            headerBytes: ByteArray?,
            titleId: Long,
            fileSize: Long,
            fileName: String,
        ): String {
            val digest = MessageDigest.getInstance("MD5")
            if (headerBytes != null && headerBytes.isNotEmpty()) {
                digest.update(headerBytes, 0, minOf(headerBytes.size, HASH_SAMPLE_BYTES))
                if (titleId != 0L) {
                    digest.update(String.format(":%016X", titleId).toByteArray(Charsets.UTF_8))
                }
            } else {
                val canonical = String.format("3DS:%016X:%d:%s", titleId, fileSize, fileName.lowercase())
                digest.update(canonical.toByteArray(Charsets.UTF_8))
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private fun resolvePath(uri: Uri): String {
        return NativeLibrary.resolveNativeOrContentPath(uri)
    }

    private fun compute3dsRetroAchievementsHash(
        romUri: Uri,
        resolvedPath: String,
        titleId: Long,
        fileName: String,
    ): String {
        var fileSize = 0L
        val sampleBytes = runCatching {
            val f = if (resolvedPath.isNotBlank() && !resolvedPath.startsWith("content://")) File(resolvedPath) else null
            if (f != null && f.isFile) {
                fileSize = f.length()
                f.inputStream().use { input ->
                    val buf = ByteArray(HASH_SAMPLE_BYTES)
                    val read = input.read(buf)
                    if (read > 0) buf.copyOf(read) else null
                }
            } else {
                context.contentResolver.openInputStream(romUri)?.use { input ->
                    val buf = ByteArray(HASH_SAMPLE_BYTES)
                    val read = input.read(buf)
                    if (read > 0) buf.copyOf(read) else null
                }
            }
        }.getOrNull()
        return compute3dsHashFromBytes(sampleBytes, titleId, fileSize, fileName)
    }

    override fun getRomFromUri(romUri: Uri, parentUri: Uri?): Rom? {
        return runCatching {
            NativeCoreLoader.load()
            val path = resolvePath(romUri)
            val fallbackFileName = uriHandler.getUriDocument(romUri)?.name
                ?: romUri.lastPathSegment?.substringAfterLast('/')
                ?: "game.3ds"
            val fallbackTitle = fallbackFileName.substringBeforeLast('.').ifBlank { "Nintendo 3DS Game" }
            val isArtic = fallbackFileName.endsWith(".artic", ignoreCase = true)
            val info = if (isArtic) null else runCatching { GameInfo(path) }.getOrNull()
            val valid = info?.let { runCatching { it.isValid() }.getOrDefault(false) } ?: false
            val title = if (valid) info!!.getTitle().ifBlank { fallbackTitle } else fallbackTitle
            val company = if (valid) runCatching { info!!.getCompany() }.getOrDefault("") else ""
            val titleId = if (valid) runCatching { info!!.getTitleID() }.getOrDefault(0L) else 0L
            val gameCode = if (titleId != 0L) String.format("%016X", titleId) else ""
            val raHash = if (isArtic) "" else compute3dsRetroAchievementsHash(romUri, path, titleId, fallbackFileName)
            Rom(
                name = title,
                developerName = company,
                fileName = fallbackFileName,
                uri = romUri,
                parentTreeUri = parentUri,
                config = RomConfig.default(),
                lastPlayed = null,
                isDsiWareTitle = false,
                isDsiEnhanced = false,
                retroAchievementsHash = raHash,
                gameCode = gameCode,
                installedDsiWareTitleId = null,
            )
        }.getOrNull()
    }

    override fun getRomIcon(rom: Rom): Bitmap? {
        return runCatching {
            NativeCoreLoader.load()
            val info = GameInfo(resolvePath(rom.uri))
            if (!info.isValid()) return@runCatching null
            val pixels = info.getIcon() ?: return@runCatching null
            if (pixels.size == 48 * 48) {
                Bitmap.createBitmap(pixels, 48, 48, Bitmap.Config.ARGB_8888)
            } else if (pixels.size == 24 * 24) {
                Bitmap.createBitmap(pixels, 24, 24, Bitmap.Config.ARGB_8888)
            } else {
                null
            }
        }.getOrNull()
    }

    override fun getRomInfo(rom: Rom): RomInfo? {
        return runCatching {
            NativeCoreLoader.load()
            val info = GameInfo(resolvePath(rom.uri))
            val valid = info.isValid()
            val titleId = if (valid) info.getTitleID() else 0L
            val code = if (titleId != 0L) String.format("%016X", titleId) else rom.gameCode
            val resolvedTitle = if (valid) info!!.getTitle().ifBlank { rom.name } else rom.name
            RomInfo(
                gameCode = code,
                headerChecksum = (titleId and 0xFFFFFFFFL).toUInt(),
                gameTitle = resolvedTitle,
                gameName = resolvedTitle,
                isDsiWareTitle = false,
            )
        }.getOrNull()
    }

    fun getRomMetadata(romUri: Uri): RomMetadata? {
        return runCatching {
            NativeCoreLoader.load()
            val resolvedPath = resolvePath(romUri)
            val info = GameInfo(resolvedPath)
            if (!info.isValid()) return@runCatching null
            val tid = info.getTitleID()
            val fallbackName = uriHandler.getUriDocument(romUri)?.name
                ?: romUri.lastPathSegment?.substringAfterLast('/')
                ?: "game.3ds"
            val raHash = if (fallbackName.endsWith(".artic", ignoreCase = true)) {
                ""
            } else {
                compute3dsRetroAchievementsHash(romUri, resolvedPath, tid, fallbackName)
            }
            RomMetadata(
                romTitle = info.getTitle(),
                developerName = info.getCompany(),
                isDSiWareTitle = false,
                isDSiEnhanced = false,
                retroAchievementsHash = raHash,
                gameCode = String.format("%016X", tid),
                titleId = tid,
            )
        }.getOrNull()
    }

    override suspend fun getRealRomUri(rom: Rom): Uri = rom.uri
}
