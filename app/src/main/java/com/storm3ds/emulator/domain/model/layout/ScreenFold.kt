package com.storm3ds.emulator.domain.model.layout

import com.storm3ds.emulator.domain.model.Rect
import com.storm3ds.emulator.domain.model.ui.Orientation

data class ScreenFold(
    val orientation: Orientation,
    val type: FoldType,
    val foldBounds: Rect,
) {
    
    enum class FoldType {
        SEAMLESS,
        GAP,
    }
}