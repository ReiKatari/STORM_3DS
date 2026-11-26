package com.storm3ds.emulator.impl

import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import com.storm3ds.emulator.R
import com.storm3ds.emulator.common.uridelegates.UriHandler
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.domain.model.SaveStateSlot
import com.storm3ds.emulator.domain.repositories.SaveStatesRepository
import com.storm3ds.emulator.domain.repositories.SettingsRepository
import com.storm3ds.emulator.extensions.nameWithoutExtension
import com.storm3ds.emulator.ui.emulator.exceptions.SaveSlotLoadException
import java.util.*

class FileSystemSaveStatesRepository(
    private val context: android.content.Context,
    private val settingsRepository: SettingsRepository,
    private val saveStateScreenshotProvider: SaveStateScreenshotProvider,
    private val uriHandler: UriHandler
) : SaveStatesRepository {

    override fun getRomSaveStates(rom: Rom): List<SaveStateSlot> {
        val saveStateDirectoryDocument = getSaveStateDirectoryDocument(rom) ?: return emptyList()
        val romFileName = getRomFileNameWithoutExtension(rom) ?: return emptyList()

        val saveStateSlots = Array(9) {
            val customName = getSaveStateCustomName(rom, it)
            SaveStateSlot(it, false, null, null, customName = customName)
        }
        val fileNameRegex = "${Regex.escape(romFileName)}\\.ml[0-8]".toRegex()
        saveStateDirectoryDocument.listFiles().forEach {
            val fileName = it.name
            if (fileName?.matches(fileNameRegex) == true) {
                val slotNumber = fileName.last().digitToInt()
                val customName = getSaveStateCustomName(rom, slotNumber)
                val slot = SaveStateSlot(slotNumber, true, Date(it.lastModified()), null, customName = customName)
                val screenshotUri = saveStateScreenshotProvider.getRomSaveStateScreenshotUri(rom, slot)
                saveStateSlots[slotNumber] = slot.copy(screenshot = screenshotUri)
            }
        }

        return saveStateSlots.toList()
    }

    override fun getRomQuickSaveStateSlot(rom: Rom): SaveStateSlot {
        val quickSaveStateDocument = getRomQuickSaveStateDocument(rom)
        val saveStateExists = quickSaveStateDocument != null
        val lastModified = quickSaveStateDocument?.let { Date(it.lastModified()) }
        val customName = getSaveStateCustomName(rom, SaveStateSlot.QUICK_SAVE_SLOT)
        val slot = SaveStateSlot(SaveStateSlot.QUICK_SAVE_SLOT, saveStateExists, lastModified, null, customName = customName)
        val screenshotUri = saveStateScreenshotProvider.getRomSaveStateScreenshotUri(rom, slot)
        return slot.copy(screenshot = screenshotUri)
    }

    override fun getPeriodicAutoSaveSlot(rom: Rom): SaveStateSlot {
        val saveStateDirectoryDocument = getSaveStateDirectoryDocument(rom)
        val romFileName = getRomFileNameWithoutExtension(rom)
        val periodicFileName = "$romFileName.periodic_autosave.ml"
        val file = saveStateDirectoryDocument?.findFile(periodicFileName)
        val exists = file != null
        val lastModified = file?.let { Date(it.lastModified()) }
        val slot = SaveStateSlot(
            slot = SaveStateSlot.PERIODIC_AUTO_SAVE_SLOT,
            exists = exists,
            lastUsedDate = lastModified,
            screenshot = null,
            customName = "Periodic Auto-Save"
        )
        val screenshotUri = if (exists) saveStateScreenshotProvider.getRomSaveStateScreenshotUri(rom, slot) else null
        return slot.copy(screenshot = screenshotUri)
    }

    override fun getPeriodicAutoSaveUri(rom: Rom): Uri {
        val saveStateDirectoryDocument = getSaveStateDirectoryDocument(rom) ?: throw SaveSlotLoadException("Could not create parent directory document")
        val romFileName = getRomFileNameWithoutExtension(rom) ?: throw SaveSlotLoadException("Could not determine ROM file name")
        val periodicFileName = "$romFileName.periodic_autosave.ml"
        val saveStateFile = saveStateDirectoryDocument.findFile(periodicFileName)
        return saveStateFile?.uri ?: (saveStateDirectoryDocument.createFile("*/*", periodicFileName)?.uri ?: throw SaveSlotLoadException("Could not create periodic auto-save file"))
    }

    override fun getRomSaveStateUri(rom: Rom, saveState: SaveStateSlot): Uri {
        val saveStateDirectoryDocument = getSaveStateDirectoryDocument(rom) ?: throw SaveSlotLoadException("Could not create parent directory document")

        val romFileName = getRomFileNameWithoutExtension(rom) ?: throw SaveSlotLoadException("Could not determine ROM file name")
        val saveStateName = if (saveState.slot == SaveStateSlot.PERIODIC_AUTO_SAVE_SLOT) {
            "$romFileName.periodic_autosave.ml"
        } else {
            "$romFileName.ml${saveState.slot}"
        }
        val saveStateFile = saveStateDirectoryDocument.findFile(saveStateName)

        val uri = if (saveStateFile != null) {
            saveStateFile.uri
        } else {
            saveStateDirectoryDocument.createFile("*/*", saveStateName)?.uri ?: throw SaveSlotLoadException("Could not create save state file")
        }

        return uri
    }

    override fun setRomSaveStateScreenshot(rom: Rom, saveState: SaveStateSlot, screenshot: Bitmap) {
        saveStateScreenshotProvider.saveRomSaveStateScreenshot(rom, saveState, screenshot)
    }

    override fun deleteRomSaveStateScreenshot(rom: Rom, saveState: SaveStateSlot) {
        saveStateScreenshotProvider.deleteRomSaveStateScreenshot(rom, saveState)
    }

    override fun deleteRomSaveState(rom: Rom, saveState: SaveStateSlot) {
        if (!saveState.exists) {
            return
        }

        val saveStateDirectoryDocument = getSaveStateDirectoryDocument(rom) ?: throw SaveSlotLoadException("Could not create parent directory document")
        val romFileName = getRomFileNameWithoutExtension(rom) ?: throw SaveSlotLoadException("Could not determine ROM file name")

        val saveStateName = if (saveState.slot == SaveStateSlot.PERIODIC_AUTO_SAVE_SLOT) {
            "$romFileName.periodic_autosave.ml"
        } else {
            "$romFileName.ml${saveState.slot}"
        }
        val saveStateFile = saveStateDirectoryDocument.findFile(saveStateName)

        saveStateFile?.delete()
        deleteNativeCstForSlot(rom, saveState.slot)
        saveStateScreenshotProvider.deleteRomSaveStateScreenshot(rom, saveState)
        setSaveStateCustomName(rom, saveState.slot, null)
    }

    override fun duplicateRomSaveState(rom: Rom, sourceSlot: SaveStateSlot, targetSlotNumber: Int) {
        if (!sourceSlot.exists) return
        val saveStateDirectoryDocument = getSaveStateDirectoryDocument(rom) ?: throw SaveSlotLoadException("Could not create parent directory document")
        val romFileName = getRomFileNameWithoutExtension(rom) ?: throw SaveSlotLoadException("Could not determine ROM file name")

        val sourceName = "$romFileName.ml${sourceSlot.slot}"
        val targetName = "$romFileName.ml$targetSlotNumber"

        val sourceFile = saveStateDirectoryDocument.findFile(sourceName) ?: return
        val targetFile = saveStateDirectoryDocument.findFile(targetName) ?: saveStateDirectoryDocument.createFile("*/*", targetName) ?: return

        context.contentResolver.openInputStream(sourceFile.uri)?.use { input ->
            val outStream = runCatching { context.contentResolver.openOutputStream(targetFile.uri, "wt") }.getOrNull()
                ?: runCatching { context.contentResolver.openOutputStream(targetFile.uri, "rwt") }.getOrNull()
                ?: runCatching {
                    val pfd = context.contentResolver.openFileDescriptor(targetFile.uri, "rw") ?: return@runCatching null
                    try {
                        Os.ftruncate(pfd.fileDescriptor, 0L)
                        ParcelFileDescriptor.AutoCloseOutputStream(pfd)
                    } catch (t: Throwable) {
                        runCatching { pfd.close() }
                        throw t
                    }
                }.getOrNull()
            outStream?.use { output ->
                input.copyTo(output)
            }
        }

        copyNativeCstForSlot(rom, sourceSlot.slot, targetSlotNumber)
        saveStateScreenshotProvider.duplicateRomSaveStateScreenshot(rom, sourceSlot, targetSlotNumber)

        val srcName = getSaveStateCustomName(rom, sourceSlot.slot)
        if (!srcName.isNullOrBlank()) {
            setSaveStateCustomName(rom, targetSlotNumber, context.getString(R.string.save_state_copy_format, srcName))
        }
    }

    override fun setSaveStateCustomName(rom: Rom, slotNumber: Int, customName: String?) {
        val prefs = context.getSharedPreferences("save_state_names", android.content.Context.MODE_PRIVATE)
        val key = "${rom.uri.hashCode()}_slot_$slotNumber"
        prefs.edit().apply {
            if (customName.isNullOrBlank()) {
                remove(key)
            } else {
                putString(key, customName.trim())
            }
            apply()
        }
    }

    override fun getSaveStateCustomName(rom: Rom, slotNumber: Int): String? {
        val prefs = context.getSharedPreferences("save_state_names", android.content.Context.MODE_PRIVATE)
        val key = "${rom.uri.hashCode()}_slot_$slotNumber"
        return prefs.getString(key, null)
    }

    private fun getRomQuickSaveStateDocument(rom: Rom): DocumentFile? {
        val saveStateDirectoryDocument = getSaveStateDirectoryDocument(rom) ?: return null
        val romFileName = getRomFileNameWithoutExtension(rom) ?: return null

        val quickSaveStateFileName = "$romFileName.ml0"
        return saveStateDirectoryDocument.findFile(quickSaveStateFileName)
    }

    private fun getSaveStateDirectoryDocument(rom: Rom): DocumentFile? {
        val saveStateDirectoryUri = settingsRepository.getSaveStateDirectory(rom) ?: return null
        return uriHandler.getUriTreeDocument(saveStateDirectoryUri)
    }

    private fun resolveNativeCstFile(rom: Rom, slotNumber: Int): java.io.File? {
        val nativeSlot = when (slotNumber) {
            SaveStateSlot.PERIODIC_AUTO_SAVE_SLOT -> 9
            else -> slotNumber.coerceIn(0, 10)
        }
        val tidHex = rom.gameCode.trim().uppercase().takeIf { it.length == 16 && it.all { ch -> ch in '0'..'9' || ch in 'A'..'F' } }
            ?: String.format(java.util.Locale.US, "%016X", com.storm3ds.emulator.MelonEmulator.getCurrentTitleId())
        if (tidHex == "0000000000000000") return null
        val quicksavesDir = java.io.File(com.storm3ds.emulator.nativebridge.NativeBridgeHost.resolveUserDirectoryPath(), "quicksaves")
        return java.io.File(quicksavesDir, "%s.%02d.cst".format(java.util.Locale.US, tidHex, nativeSlot))
    }

    private fun deleteNativeCstForSlot(rom: Rom, slotNumber: Int) {
        runCatching {
            val cst = resolveNativeCstFile(rom, slotNumber) ?: return@runCatching
            if (cst.isFile) {
                cst.delete()
            }
        }
    }

    private fun copyNativeCstForSlot(rom: Rom, sourceSlotNumber: Int, targetSlotNumber: Int) {
        runCatching {
            val srcCst = resolveNativeCstFile(rom, sourceSlotNumber) ?: return@runCatching
            val dstCst = resolveNativeCstFile(rom, targetSlotNumber) ?: return@runCatching
            if (srcCst.isFile) {
                dstCst.parentFile?.mkdirs()
                srcCst.copyTo(dstCst, overwrite = true)
            } else if (dstCst.isFile) {
                dstCst.delete()
            }
        }
    }

    private fun getRomFileNameWithoutExtension(rom: Rom): String? {
        val romDocument = uriHandler.getUriDocument(rom.uri)
        return romDocument?.nameWithoutExtension?.takeIf { it.isNotBlank() }
            ?: rom.fileName.substringBeforeLast('.').takeIf { it.isNotBlank() }
            ?: rom.uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
    }
}
