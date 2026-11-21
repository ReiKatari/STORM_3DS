package com.storm3ds.emulator.ui.layouteditor.model

import com.storm3ds.emulator.domain.model.layout.BackgroundMode
import java.util.UUID

data class LayoutBackgroundProperties(
    val backgroundId: UUID?,
    val backgroundMode: BackgroundMode,
)