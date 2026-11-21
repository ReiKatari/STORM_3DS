package com.storm3ds.emulator.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import com.storm3ds.emulator.domain.model.layout.Insets

data class InsetsDto(
    @SerializedName("left")
    val left: Int,
    @SerializedName("top")
    val top: Int,
    @SerializedName("right")
    val right: Int,
    @SerializedName("bottom")
    val bottom: Int
) {

    fun toModel(): Insets {
        return Insets(left, top, right, bottom)
    }

    companion object {
        fun fromModel(insets: Insets): InsetsDto {
            return InsetsDto(insets.left, insets.top, insets.right, insets.bottom)
        }
    }
}
