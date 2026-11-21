package com.storm3ds.emulator.domain.model

import androidx.annotation.Keep

@Keep
enum class VulkanPipelineProfile {
    COMPATIBILITY,
    FASTPATH;

    val usesFastPath: Boolean
        get() = this == FASTPATH

    companion object {
        fun fromFastPathPreference(enabled: Boolean): VulkanPipelineProfile {
            return if (enabled) FASTPATH else COMPATIBILITY
        }
    }
}
