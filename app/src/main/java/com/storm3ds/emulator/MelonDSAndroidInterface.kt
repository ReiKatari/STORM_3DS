package com.storm3ds.emulator

import com.storm3ds.emulator.common.UriFileHandler
import com.storm3ds.emulator.domain.model.VulkanDriverConfiguration
import com.storm3ds.emulator.domain.model.VulkanDriverMode
import com.storm3ds.emulator.domain.model.VulkanPipelineProfile
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.nativebridge.utils.GpuDriverHelper

data class VulkanGpuHardwareInfo(
    val deviceName: String = "",
    val vendorId: Int = 0,
    val deviceId: Int = 0,
    val apiVersion: String = "",
    val driverVersionRaw: Long = 0L,
    val driverName: String = "",
    val driverInfo: String = "",
    val isCustomDriver: Boolean = false,
)

object MelonDSAndroidInterface {
    const val RENDERER_CAP_OPENGL = 1 shl 0
    const val RENDERER_CAP_VULKAN = 1 shl 1

    @Volatile
    private var lastCustomDriver: Boolean = false
    @Volatile
    private var lastDriverName: String = "System GPU Driver"

    fun setup(uriFileHandler: UriFileHandler, vulkanDriverConfiguration: VulkanDriverConfiguration) {
        NativeCoreLoader.load()
        configureVulkanDriver(vulkanDriverConfiguration)
    }

    fun configureVulkanDriver(vulkanDriverConfiguration: VulkanDriverConfiguration) {
        NativeCoreLoader.load()
        val isCustom = vulkanDriverConfiguration.mode == VulkanDriverMode.CUSTOM &&
            !vulkanDriverConfiguration.customDriverName.isNullOrBlank()
        lastCustomDriver = isCustom
        lastDriverName = vulkanDriverConfiguration.customDriverDisplayName
            ?: vulkanDriverConfiguration.customDriverName
            ?: "System GPU Driver"
        if (isCustom) {
            NativeLibrary.initializeGpuDriver(
                vulkanDriverConfiguration.hookLibDir,
                vulkanDriverConfiguration.customDriverDir,
                vulkanDriverConfiguration.customDriverName,
                vulkanDriverConfiguration.tmpLibDir,
            )
        } else {
            GpuDriverHelper.initializeDriverParameters(null, null)
        }
    }

    fun getEmulatorGlContext(): Long = 0L

    fun getRendererCapabilities(): Int = RENDERER_CAP_OPENGL or RENDERER_CAP_VULKAN

    fun isVulkanRendererSupported(): Boolean = true

    fun canInitializeVulkanRenderer(profile: VulkanPipelineProfile): Boolean = true

    fun setVulkanCompatibilityOverrides(
        disableTimelineSemaphores: Boolean,
        disableDynamicTextureIndexing: Boolean,
    ) {
        // Handled natively via SPIR-V optimizer & Vulkan backend inside libstorm3ds.so
    }

    fun getVulkanGpuInfo(): VulkanGpuHardwareInfo {
        return VulkanGpuHardwareInfo(
            deviceName = android.os.Build.HARDWARE,
            apiVersion = "Vulkan 1.1+ / OpenGL ES 3.2",
            driverName = lastDriverName,
            isCustomDriver = lastCustomDriver,
        )
    }

    fun cleanup() {
        // Native resources cleaned on NativeLibrary.stopEmulation()
    }
}
