package com.storm3ds.emulator.migrations.legacy

import com.google.gson.annotations.SerializedName
import com.storm3ds.emulator.domain.model.rom.config.RuntimeConsoleType
import com.storm3ds.emulator.domain.model.rom.config.RuntimeMicSource

data class RomConfigDto31(
    @SerializedName("runtimeConsoleType")
    val runtimeConsoleType: RuntimeConsoleType,
    @SerializedName("runtimeMicSource")
    val runtimeMicSource: RuntimeMicSource,
    @SerializedName("layoutId")
    val layoutId: String?,
    @SerializedName("gbaSlotConfig")
    val gbaSlotConfig: RomGbaSlotConfigDto31,
)