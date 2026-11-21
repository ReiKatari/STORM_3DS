package com.storm3ds.emulator.ui.emulator.render

import com.storm3ds.emulator.domain.model.RuntimeBackground
import com.storm3ds.emulator.ui.emulator.EmulatorSurfaceView
import com.storm3ds.emulator.ui.emulator.model.VulkanPresentationConfig

interface FrameRenderCoordinator {
    fun addSurface(surface: EmulatorSurfaceView)
    fun removeSurface(surface: EmulatorSurfaceView)
    fun updateSurfacePresentation(surface: EmulatorSurfaceView, config: VulkanPresentationConfig?, background: RuntimeBackground)
    fun renderFrame(frameDeadlineNanos: Long?)

    fun prewarmShaders(atlasWidth: Int, atlasHeight: Int): Long = 0L

    fun stop()
}
