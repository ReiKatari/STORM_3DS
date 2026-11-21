package com.storm3ds.emulator.ui.emulator.model

import androidx.annotation.Keep
import com.storm3ds.emulator.domain.model.Rect
import com.storm3ds.emulator.domain.model.VideoFiltering
import com.storm3ds.emulator.domain.model.layout.BackgroundMode

@Keep
data class VulkanPresentationConfig(
    val topScreenRect: Rect?,
    val bottomScreenRect: Rect?,
    val topAlpha: Float,
    val bottomAlpha: Float,
    val topOnTop: Boolean,
    val bottomOnTop: Boolean,
    val hybridTopScreenRect: Rect?,
    val hybridBottomScreenRect: Rect?,
    val hybridAlpha: Float,
    val hybridOnTop: Boolean,
    val backgroundMode: BackgroundMode,
    val videoFiltering: VideoFiltering,
    val retroShaderEnabled: Boolean,
    val retroShaderPresetPath: String?,
    val retroShaderSourceResolution: String,
    val retroShaderPassCount: Int,
    val retroShaderParameterOverrides: Map<String, Float>,
    val retroShaderClearHistory: Boolean,
)
