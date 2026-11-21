package com.storm3ds.emulator.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import com.storm3ds.emulator.domain.model.layout.LayoutDisplayPair

data class LayoutDisplayPairDto(
    @SerializedName("mainScreenDisplay")
    val mainScreenDisplay: LayoutDisplayDto,
    @SerializedName("secondaryScreenDisplay")
    val secondaryScreenDisplay: LayoutDisplayDto?,
) {

    fun toModel(): LayoutDisplayPair {
        return LayoutDisplayPair(
            mainScreenDisplay = mainScreenDisplay.toModel(),
            secondaryScreenDisplay = secondaryScreenDisplay?.toModel(),
        )
    }

    companion object {
        fun fromModel(layoutDisplayPair: LayoutDisplayPair): LayoutDisplayPairDto {
            return LayoutDisplayPairDto(
                mainScreenDisplay = LayoutDisplayDto.fromModel(layoutDisplayPair.mainScreenDisplay),
                secondaryScreenDisplay = layoutDisplayPair.secondaryScreenDisplay?.let { LayoutDisplayDto.fromModel(it) },
            )
        }
    }
}