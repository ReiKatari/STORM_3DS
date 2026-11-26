package com.storm3ds.emulator.common.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.storm3ds.emulator.domain.model.VideoRenderer

object DevicePerformanceProfiler {

    enum class DeviceTier {
        LEGACY_LOW,
        MID_RANGE,
        HIGH_END,
        FLAGSHIP,
    }

    data class DeviceProfile(
        val tier: DeviceTier,
        val totalRamMb: Int,
        val cpuCores: Int,
        val glesVersion: Int,
        val gpuVendorHint: String,
        val recommendedRenderer: VideoRenderer,
        val recommendedResolutionScale: Int,
        val recommendedDynarmicCacheMb: Int,
        val recommendedAsyncShaders: Boolean,
        val recommendedSkipSlowDraw: Boolean,
        val recommendedImageCachePercent: Int,
        val isLegacyOrLowEnd: Boolean,
        val supportsHighRefreshRate: Boolean,
    )

    @Volatile
    private var cachedProfile: DeviceProfile? = null

    fun profile(context: Context, isVulkanSupported: Boolean): DeviceProfile {
        cachedProfile?.let { return it }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        runCatching { am?.getMemoryInfo(memInfo) }
        val totalRamMb = (memInfo.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(1024)
        val glesVersion = runCatching { am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0 }.getOrDefault(0)
        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

        val hw = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.lowercase() else ""
        val combined = "$hw $board $soc"

        val isLegacyGpu = combined.contains("powervr") ||
            combined.contains("mt673") ||
            combined.contains("mt675") ||
            combined.contains("mt65") ||
            combined.contains("mali-t") ||
            combined.contains("adreno 3") ||
            combined.contains("adreno 4") ||
            combined.contains("adreno 5") ||
            glesVersion < 0x30002

        val isFlagshipChip = combined.contains("sm8650") ||
            combined.contains("sm8750") ||
            combined.contains("sm8550") ||
            combined.contains("sm8475") ||
            combined.contains("kalama") ||
            combined.contains("pineapple") ||
            combined.contains("sun") ||
            combined.contains("mt6989") ||
            combined.contains("mt6991") ||
            combined.contains("dimensity 93") ||
            combined.contains("dimensity 94") ||
            (totalRamMb >= 10000 && cpuCores >= 8 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)

        val tier = when {
            isLegacyGpu || totalRamMb <= 3500 || Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> DeviceTier.LEGACY_LOW
            isFlagshipChip -> DeviceTier.FLAGSHIP
            totalRamMb >= 7000 && cpuCores >= 8 -> DeviceTier.HIGH_END
            else -> DeviceTier.MID_RANGE
        }

        val gpuVendorHint = when {
            combined.contains("qcom") || combined.contains("adreno") || combined.contains("sm8") -> "Adreno / Qualcomm"
            combined.contains("exynos") || combined.contains("s5e") || combined.contains("xclipse") -> "Exynos / Xclipse"
            combined.contains("mt") || combined.contains("dimensity") || combined.contains("mali") -> "MediaTek / Mali"
            combined.contains("powervr") -> "PowerVR"
            else -> Build.HARDWARE.ifBlank { "Standard GPU" }
        }

        val recommendedRenderer = when {
            !isVulkanSupported -> VideoRenderer.OPENGL
            tier == DeviceTier.LEGACY_LOW -> if (glesVersion >= 0x30002) VideoRenderer.OPENGL else VideoRenderer.VULKAN
            else -> VideoRenderer.VULKAN
        }

        val recommendedRes = when (tier) {
            DeviceTier.LEGACY_LOW -> 1
            DeviceTier.MID_RANGE -> 2
            DeviceTier.HIGH_END -> 2
            DeviceTier.FLAGSHIP -> 3
        }

        val recommendedJitMb = when (tier) {
            DeviceTier.LEGACY_LOW -> 48
            DeviceTier.MID_RANGE -> 96
            DeviceTier.HIGH_END -> 128
            DeviceTier.FLAGSHIP -> 192
        }

        val imageCachePct = when (tier) {
            DeviceTier.LEGACY_LOW -> 10
            DeviceTier.MID_RANGE -> 15
            DeviceTier.HIGH_END -> 20
            DeviceTier.FLAGSHIP -> 25
        }

        return DeviceProfile(
            tier = tier,
            totalRamMb = totalRamMb,
            cpuCores = cpuCores,
            glesVersion = glesVersion,
            gpuVendorHint = gpuVendorHint,
            recommendedRenderer = recommendedRenderer,
            recommendedResolutionScale = recommendedRes,
            recommendedDynarmicCacheMb = recommendedJitMb,
            recommendedAsyncShaders = true,
            recommendedSkipSlowDraw = (tier == DeviceTier.LEGACY_LOW),
            recommendedImageCachePercent = imageCachePct,
            isLegacyOrLowEnd = (tier == DeviceTier.LEGACY_LOW),
            supportsHighRefreshRate = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && tier != DeviceTier.LEGACY_LOW,
        ).also { cachedProfile = it }
    }
}
