package com.storm3ds.emulator.ui.emulator.model

import com.storm3ds.emulator.domain.model.Rect

data class ExternalLayoutState(
    val layoutWidth: Int,
    val layoutHeight: Int,
    val topScreen: Screen?,
    val bottomScreen: Screen?,
) {
    data class Screen(
        val rect: Rect,
        val alpha: Float,
        val onTop: Boolean,
    )
}
