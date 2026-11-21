package com.storm3ds.emulator.domain.services

import android.net.Uri
import com.storm3ds.emulator.domain.model.DSiWareTitle
import com.storm3ds.emulator.domain.model.dsinand.ImportDSiWareTitleResult
import com.storm3ds.emulator.domain.model.dsinand.OpenDSiNandResult
import com.storm3ds.emulator.domain.model.dsinand.DSiWareTitleFileType

interface DSiNandManager {
    suspend fun openNand(): OpenDSiNandResult
    suspend fun listTitles(): List<DSiWareTitle>
    suspend fun importTitle(titleUri: Uri): ImportDSiWareTitleResult
    suspend fun deleteTitle(title: DSiWareTitle)
    suspend fun deleteTitle(titleId: Long)
    suspend fun repairTitleSaves(titleId: Long): Boolean
    suspend fun ensureTitleSaveStructure(titleId: Long, romHeaderBytes: ByteArray, tmdMetadata: ByteArray? = null): Boolean
    suspend fun exportTitleExecutable(titleId: Long, outputPath: String): Boolean
    suspend fun importTitleFileFromPath(titleId: Long, fileType: DSiWareTitleFileType, filePath: String): Boolean
    suspend fun exportTitleFileToPath(titleId: Long, fileType: DSiWareTitleFileType, filePath: String): Boolean
    suspend fun importTitleFile(title: DSiWareTitle, fileType: DSiWareTitleFileType, fileUri: Uri): Boolean
    suspend fun importTitleFile(titleId: Long, fileType: DSiWareTitleFileType, fileUri: Uri): Boolean
    suspend fun exportTitleFile(title: DSiWareTitle, fileType: DSiWareTitleFileType, fileUri: Uri): Boolean
    suspend fun exportTitleFile(titleId: Long, fileType: DSiWareTitleFileType, fileUri: Uri): Boolean
    fun closeNand()
}
