package com.storm3ds.emulator.impl.dtos.layout

import com.storm3ds.emulator.domain.model.layout.ScreenFold
import com.storm3ds.emulator.utils.enumValueOfIgnoreCase

data class ScreenFoldDto(
    val orientation: String,
    val type: String,
    val foldBounds: RectDto,
) {

    fun toModel(): ScreenFold {
        return ScreenFold(
            enumValueOfIgnoreCase(orientation),
            enumValueOfIgnoreCase(type),
            foldBounds.toModel(),
        )
    }

    companion object {
        fun fromModel(fold: ScreenFold): ScreenFoldDto {
            return ScreenFoldDto(
                fold.orientation.name,
                fold.type.name,
                RectDto.fromModel(fold.foldBounds),
            )
        }
    }
}