package com.storm3ds.emulator.domain.model.layout

import com.storm3ds.emulator.domain.model.Point
import com.storm3ds.emulator.domain.model.ui.Orientation

data class UILayoutVariant(
    val uiSize: Point,
    val uiInsets: Insets,
    val orientation: Orientation,
    val folds: List<ScreenFold>,
    val displays: LayoutDisplayPair,
)