package com.storm3ds.emulator.domain.repositories

import android.graphics.Bitmap
import android.net.Uri
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.domain.model.SaveStateSlot

interface SaveStatesRepository {
    fun getRomSaveStates(rom: Rom): List<SaveStateSlot>
    fun getRomQuickSaveStateSlot(rom: Rom): SaveStateSlot
    fun getRomSaveStateUri(rom: Rom, saveState: SaveStateSlot): Uri
    fun getPeriodicAutoSaveSlot(rom: Rom): SaveStateSlot
    fun getPeriodicAutoSaveUri(rom: Rom): Uri
    fun setRomSaveStateScreenshot(rom: Rom, saveState: SaveStateSlot, screenshot: Bitmap)
    fun deleteRomSaveStateScreenshot(rom: Rom, saveState: SaveStateSlot)
    fun deleteRomSaveState(rom: Rom, saveState: SaveStateSlot)
    fun duplicateRomSaveState(rom: Rom, sourceSlot: SaveStateSlot, targetSlotNumber: Int)
    fun setSaveStateCustomName(rom: Rom, slotNumber: Int, customName: String?)
    fun getSaveStateCustomName(rom: Rom, slotNumber: Int): String?
}
