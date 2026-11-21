package com.storm3ds.emulator.domain.model

import com.storm3ds.emulator.common.retroarch.RetroArchShaderPreset

data class RetroArchShaderConfiguration(
    val presetPath: String?,
    val sourceResolution: RetroArchShaderSourceResolution,
    val passCount: Int,
    val sourceBytes: Long,
    val parameterOverrides: Map<String, Float>,
    val clearHistory: Boolean,
) {
    val estimatedCompileMillis: Long
        get() = RetroArchShaderPreset.Weight(passCount, sourceBytes).estimatedCompileMillis
}
