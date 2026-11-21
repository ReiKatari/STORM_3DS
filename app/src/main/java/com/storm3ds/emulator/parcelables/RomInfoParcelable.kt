package com.storm3ds.emulator.parcelables

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.storm3ds.emulator.domain.model.RomInfo

@Parcelize
class RomInfoParcelable(
    private val gameCode: String,
    private val headerChecksum: Int,
    private val gameTitle: String,
    private val gameName: String,
    private val isDsiWareTitle: Boolean = false,
) : Parcelable {
    companion object {
        fun fromRomInfo(romInfo: RomInfo): RomInfoParcelable {
            return RomInfoParcelable(romInfo.gameCode, romInfo.headerChecksum.toInt(), romInfo.gameTitle, romInfo.gameName, romInfo.isDsiWareTitle)
        }
    }

    fun toRomInfo(): RomInfo {
        return RomInfo(gameCode, headerChecksum.toUInt(), gameTitle, gameName, isDsiWareTitle)
    }
}