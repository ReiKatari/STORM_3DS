package com.storm3ds.emulator.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import com.storm3ds.emulator.domain.model.layout.UILayoutVariant

data class UILayoutVariantDto(
    @SerializedName("uiSize")
    val uiSize: PointDto,
    @SerializedName("insets")
    val insets: InsetsDto,
    @SerializedName("orientation")
    val orientation: String,
    @SerializedName("folds")
    val folds: List<ScreenFoldDto>,
    @SerializedName("displays")
    val displays: LayoutDisplayPairDto,
) {

    fun toModel(): UILayoutVariant {
        return UILayoutVariant(
            uiSize.toModel(),
            insets.toModel(),
            runCatching { enumValueOf<com.storm3ds.emulator.domain.model.ui.Orientation>(orientation) }.getOrDefault(com.storm3ds.emulator.domain.model.ui.Orientation.PORTRAIT),
            folds.map {
                it.toModel()
            },
            displays.toModel(),
        )
    }

    companion object {
        fun fromModel(uiLayoutVariant: UILayoutVariant): UILayoutVariantDto {
            return UILayoutVariantDto(
                PointDto.fromModel(uiLayoutVariant.uiSize),
                InsetsDto.fromModel(uiLayoutVariant.uiInsets),
                uiLayoutVariant.orientation.name,
                uiLayoutVariant.folds.map {
                    ScreenFoldDto.fromModel(it)
                },
                LayoutDisplayPairDto.fromModel(uiLayoutVariant.displays),
            )
        }
    }
}