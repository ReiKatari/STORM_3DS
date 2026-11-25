package com.storm3ds.emulator.parcelables

import android.os.Parcel
import android.os.Parcelable
import com.storm3ds.emulator.domain.model.DualScreenPreset
import com.storm3ds.emulator.domain.model.VideoFiltering
import com.storm3ds.emulator.domain.model.VideoRenderer
import com.storm3ds.emulator.domain.model.rom.config.RomConfig
import com.storm3ds.emulator.domain.model.rom.config.RomInputMode
import com.storm3ds.emulator.domain.model.rom.config.RuntimeConsoleType
import com.storm3ds.emulator.domain.model.rom.config.RuntimeMicSource
import com.storm3ds.emulator.extensions.parcelable
import java.util.UUID

class RomConfigParcelable : Parcelable {
    val romConfig: RomConfig

    constructor(romConfig: RomConfig) {
        this.romConfig = romConfig
    }

    private constructor(parcel: Parcel) {
        romConfig = RomConfig(
            runtimeConsoleType = RuntimeConsoleType.entries[parcel.readInt()],
            runtimeMicSource = RuntimeMicSource.entries[parcel.readInt()],
            layoutId = parcel.readString()?.let { UUID.fromString(it) },
            gbaSlotConfig = parcel.parcelable<RomGbaSlotConfigParcelable>()!!.gbaSlotConfig,
            customName = parcel.readString(),
            useHgEngineFix = parcel.readByte().toInt() != 0,
            inputMode = RomInputMode.entries[parcel.readInt()],
            customControllerConfiguration = parcel.parcelable<ControllerConfigurationParcelable>()?.controllerConfiguration,
            videoRenderer = parcel.readNullableEnum(VideoRenderer.entries),
            threadedRendering = parcel.readNullableBoolean(),
            internalResolutionScaling = parcel.readNullableInt(),
            videoFiltering = parcel.readNullableEnum(VideoFiltering.entries),
            retroArchShaderPresetPath = parcel.readString(),
            retroArchShaderParameters = parcel.readString(),
            retroAchievementsEnabled = parcel.readNullableBoolean(),
            dualScreenPreset = parcel.readNullableEnum(DualScreenPreset.entries),
            dualScreenKeepAspectRatio = parcel.readNullableBoolean(),
            dualScreenIntegerScale = parcel.readNullableBoolean(),
            translatorSourceLang = parcel.readString(),
            arm9Overclock = parcel.readNullableInt(),
            fastForwardSpeedMultiplier = parcel.readNullableFloat(),
            customVulkanDriverName = parcel.readString(),
            isNew3ds = parcel.readNullableBoolean(),
            cpuClockPercentage = parcel.readNullableInt(),
            render3dMode = parcel.readNullableInt(),
            render3dFactor = parcel.readNullableInt(),
            useLimitFramesAheadOfGpu = parcel.readNullableBoolean(),
            skipDuplicateFrames = parcel.readNullableBoolean(),
            disableRightEyeRender = parcel.readNullableBoolean(),
            textureFilter = parcel.readNullableInt(),
            simulate3dsGpuTimings = parcel.readNullableBoolean(),
            simulateHeadphonesPlugged = parcel.readNullableBoolean(),
            enableRealtimeAudio = parcel.readNullableBoolean(),
        )
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(romConfig.runtimeConsoleType.ordinal)
        dest.writeInt(romConfig.runtimeMicSource.ordinal)
        dest.writeString(romConfig.layoutId?.toString())
        dest.writeParcelable(RomGbaSlotConfigParcelable(romConfig.gbaSlotConfig), 0)
        dest.writeString(romConfig.customName)
        dest.writeByte((if (romConfig.useHgEngineFix) 1 else 0).toByte())
        dest.writeInt(romConfig.inputMode.ordinal)
        dest.writeParcelable(romConfig.customControllerConfiguration?.let { ControllerConfigurationParcelable(it) }, 0)
        dest.writeNullableEnum(romConfig.videoRenderer)
        dest.writeNullableBoolean(romConfig.threadedRendering)
        dest.writeNullableInt(romConfig.internalResolutionScaling)
        dest.writeNullableEnum(romConfig.videoFiltering)
        dest.writeString(romConfig.retroArchShaderPresetPath)
        dest.writeString(romConfig.retroArchShaderParameters)
        dest.writeNullableBoolean(romConfig.retroAchievementsEnabled)
        dest.writeNullableEnum(romConfig.dualScreenPreset)
        dest.writeNullableBoolean(romConfig.dualScreenKeepAspectRatio)
        dest.writeNullableBoolean(romConfig.dualScreenIntegerScale)
        dest.writeString(romConfig.translatorSourceLang)
        dest.writeNullableInt(romConfig.arm9Overclock)
        dest.writeNullableFloat(romConfig.fastForwardSpeedMultiplier)
        dest.writeString(romConfig.customVulkanDriverName)
        dest.writeNullableBoolean(romConfig.isNew3ds)
        dest.writeNullableInt(romConfig.cpuClockPercentage)
        dest.writeNullableInt(romConfig.render3dMode)
        dest.writeNullableInt(romConfig.render3dFactor)
        dest.writeNullableBoolean(romConfig.useLimitFramesAheadOfGpu)
        dest.writeNullableBoolean(romConfig.skipDuplicateFrames)
        dest.writeNullableBoolean(romConfig.disableRightEyeRender)
        dest.writeNullableInt(romConfig.textureFilter)
        dest.writeNullableBoolean(romConfig.simulate3dsGpuTimings)
        dest.writeNullableBoolean(romConfig.simulateHeadphonesPlugged)
        dest.writeNullableBoolean(romConfig.enableRealtimeAudio)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RomConfigParcelable> {
        override fun createFromParcel(parcel: Parcel): RomConfigParcelable {
            return RomConfigParcelable(parcel)
        }

        override fun newArray(size: Int): Array<RomConfigParcelable?> {
            return arrayOfNulls(size)
        }
    }
}

private fun <T : Enum<T>> Parcel.readNullableEnum(values: List<T>): T? {
    val ordinal = readInt()
    return if (ordinal >= 0) values[ordinal] else null
}

private fun Parcel.writeNullableEnum(value: Enum<*>?) {
    writeInt(value?.ordinal ?: -1)
}

private fun Parcel.readNullableBoolean(): Boolean? {
    return when (readInt()) {
        0 -> false
        1 -> true
        else -> null
    }
}

private fun Parcel.writeNullableBoolean(value: Boolean?) {
    writeInt(
        when (value) {
            false -> 0
            true -> 1
            null -> -1
        }
    )
}

private fun Parcel.readNullableInt(): Int? {
    val value = readInt()
    return if (value >= 0) value else null
}

private fun Parcel.writeNullableInt(value: Int?) {
    writeInt(value ?: -1)
}

private fun Parcel.readNullableFloat(): Float? {
    val isPresent = readByte().toInt() != 0
    return if (isPresent) readFloat() else null
}

private fun Parcel.writeNullableFloat(value: Float?) {
    if (value != null) {
        writeByte(1)
        writeFloat(value)
    } else {
        writeByte(0)
    }
}
