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

class ThreeDsRomFileProcessor(
    private val context: Context,
    private val uriHandler: UriHandler,
) : RomFileProcessor {

    private fun resolvePath(uri: Uri): String {
        if (uri.scheme == "file") return uri.path ?: uri.toString()
        val native = runCatching { NativeLibrary.getNativePath(uri) }.getOrNull()
        if (!native.isNullOrBlank()) return native
        return uri.toString()
    }

    override fun getRomFromUri(romUri: Uri, parentUri: Uri?): Rom? {
        return runCatching {
            NativeCoreLoader.load()
            val path = resolvePath(romUri)
            val fallbackFileName = uriHandler.getUriDocument(romUri)?.name
                ?: romUri.lastPathSegment?.substringAfterLast('/')
                ?: "game.3ds"
            val fallbackTitle = fallbackFileName.substringBeforeLast('.').ifBlank { "Nintendo 3DS Game" }
            val info = runCatching { GameInfo(path) }.getOrNull()
            val valid = info?.let { runCatching { it.isValid() }.getOrDefault(false) } ?: false
            val title = if (valid) info!!.getTitle().ifBlank { fallbackTitle } else fallbackTitle
            val company = if (valid) runCatching { info!!.getCompany() }.getOrDefault("") else ""
            val titleId = if (valid) runCatching { info!!.getTitleID() }.getOrDefault(0L) else 0L
            val gameCode = if (titleId != 0L) String.format("%016X", titleId) else ""
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
                retroAchievementsHash = gameCode,
                gameCode = gameCode,
                installedDsiWareTitleId = titleId.takeIf { it != 0L },
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
            val info = GameInfo(resolvePath(romUri))
            if (!info.isValid()) return@runCatching null
            val tid = info.getTitleID()
            RomMetadata(
                romTitle = info.getTitle(),
                developerName = info.getCompany(),
                isDSiWareTitle = false,
                isDSiEnhanced = false,
                retroAchievementsHash = String.format("%016X", tid),
                gameCode = String.format("%016X", tid),
                titleId = tid,
            )
        }.getOrNull()
    }

    override suspend fun getRealRomUri(rom: Rom): Uri = rom.uri
}
