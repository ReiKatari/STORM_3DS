package com.storm3ds.emulator.ui.emulator.model

import com.storm3ds.emulator.domain.model.RetroArchShaderConfiguration
import com.storm3ds.emulator.domain.model.VideoFiltering
import com.storm3ds.emulator.domain.model.VideoRenderer

data class RuntimeRendererConfiguration(
    val renderer: VideoRenderer,
    val videoFiltering: VideoFiltering,
    val resolutionScaling: Int,
    val retroArchShader: RetroArchShaderConfiguration,
)
