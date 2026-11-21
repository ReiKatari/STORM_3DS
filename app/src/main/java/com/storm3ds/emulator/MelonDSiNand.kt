package com.storm3ds.emulator

import com.storm3ds.emulator.domain.model.DSiWareTitle
import com.storm3ds.emulator.domain.model.EmulatorConfiguration

object MelonDSiNand {
    fun openNand(emulatorConfiguration: EmulatorConfiguration): Int = -1
    fun listTitles(): ArrayList<DSiWareTitle> = ArrayList()
    fun importTitle(titleUri: String, tmdMetadata: ByteArray): Int = -1
    fun deleteTitle(titleId: Int) = Unit
    fun repairTitleSaves(titleId: Int): Boolean = false
    fun ensureTitleSaveStructure(titleId: Int, romHeaderBytes: ByteArray, tmdMetadata: ByteArray?): Boolean = false
    fun ensureSystemFontTable(fontData: ByteArray): Boolean = false
    fun exportTitleExecutable(titleId: Int, outputPath: String): Boolean = false
    fun importTitleFile(titleId: Int, fileType: Int, fileUri: String): Boolean = false
    fun exportTitleFile(titleId: Int, fileType: Int, fileUri: String): Boolean = false
    fun closeNand() = Unit
}
