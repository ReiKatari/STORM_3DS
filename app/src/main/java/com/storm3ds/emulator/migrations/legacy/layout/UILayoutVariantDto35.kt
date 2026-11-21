package com.storm3ds.emulator.migrations.legacy.layout

import com.google.gson.annotations.SerializedName
import com.storm3ds.emulator.impl.dtos.layout.PointDto
import com.storm3ds.emulator.impl.dtos.layout.ScreenFoldDto

data class UILayoutVariantDto35(
    @SerializedName("uiSize")
    val uiSize: PointDto,
    @SerializedName("orientation")
    val orientation: String,
    @SerializedName("folds")
    val folds: List<ScreenFoldDto>,
)