package com.storm3ds.emulator.common.romprocessors

import android.graphics.Bitmap
import android.net.Uri
import com.storm3ds.emulator.domain.model.RomInfo
import com.storm3ds.emulator.domain.model.rom.Rom

interface RomFileProcessor {
    fun getRomFromUri(romUri: Uri, parentUri: Uri?): Rom?
    fun getRomIcon(rom: Rom): Bitmap?
    fun getRomInfo(rom: Rom): RomInfo?
    suspend fun getRealRomUri(rom: Rom): Uri?
}