package com.storm3ds.emulator.ui.layouteditor.model

import com.storm3ds.emulator.domain.model.layout.LayoutConfiguration
import com.storm3ds.emulator.domain.model.layout.UILayout
import com.storm3ds.emulator.domain.model.ui.Orientation

data class CurrentLayoutState(
    val layout: UILayout,
    val orientation: LayoutConfiguration.LayoutOrientation,
    val systemOrientation: Orientation = Orientation.PORTRAIT,
    val layoutResetToken: Long = 0L,
)