package com.storm3ds.emulator.domain.model.layout

import com.storm3ds.emulator.domain.model.Rect

data class PositionedLayoutComponent(
    val rect: Rect,
    val component: LayoutComponent,
    val alpha: Float = 1f,
    val onTop: Boolean = false,
    val locked: Boolean = false,
) {

    fun isScreen(): Boolean {
        return component.isScreen()
    }
}