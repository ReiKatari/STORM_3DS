package com.storm3ds.emulator.ui.layouteditor.model

import com.storm3ds.emulator.domain.model.layout.LayoutComponent

data class LayoutComponentPositionEditorState(
    val component: LayoutComponent,
    val x: Int,
    val y: Int,
    val maxX: Int,
    val maxY: Int,
)
