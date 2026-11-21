package com.storm3ds.emulator.ui.emulator.model

import com.storm3ds.emulator.domain.model.input.SoftInputBehaviour
import com.storm3ds.emulator.domain.model.layout.LayoutConfiguration
import com.storm3ds.emulator.domain.model.layout.UILayout

data class RuntimeInputLayoutConfiguration(
    val softInputBehaviour: SoftInputBehaviour,
    val softInputOpacity: Int,
    val isHapticFeedbackEnabled: Boolean,
    val layoutOrientation: LayoutConfiguration.LayoutOrientation,
    val layout: UILayout,
)