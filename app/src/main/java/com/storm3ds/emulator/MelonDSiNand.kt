package com.storm3ds.emulator

import com.storm3ds.emulator.domain.model.DSiWareTitle
import com.storm3ds.emulator.domain.model.EmulatorConfiguration

object MelonDSiNand {
    external fun openNand(emulatorConfiguration: EmulatorConfiguration): Int
    external fun listTitles(): ArrayList<DSiWareTitle>
    external fun importTitle(titleUri: String, tmdMetadata: ByteArray): Int
    external fun deleteTitle(titleId: Int)
    external fun repairTitleSaves(titleId: Int): Boolean
    external fun ensureTitleSaveStructure(titleId: Int, romHeaderBytes: ByteArray, tmdMetadata: ByteArray?): Boolean
    external fun ensureSystemFontTable(fontData: ByteArray): Boolean
    external fun exportTitleExecutable(titleId: Int, outputPath: String): Boolean
    external fun importTitleFile(titleId: Int, fileType: Int, fileUri: String): Boolean
    external fun exportTitleFile(titleId: Int, fileType: Int, fileUri: String): Boolean
    external fun closeNand()
}
