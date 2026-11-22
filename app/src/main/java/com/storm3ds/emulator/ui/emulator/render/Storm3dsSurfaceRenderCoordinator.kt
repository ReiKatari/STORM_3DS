package com.storm3ds.emulator.ui.emulator.render

import android.content.Context
import android.view.Surface
import com.storm3ds.emulator.domain.model.Rect
import com.storm3ds.emulator.domain.model.RuntimeBackground
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.ui.emulator.EmulatorSurfaceView
import com.storm3ds.emulator.ui.emulator.model.VulkanPresentationConfig

class Storm3dsSurfaceRenderCoordinator(
    private val context: Context,
) : FrameRenderCoordinator, EmulatorSurfaceView.SurfaceLifecycleListener {

    companion object {
        @Volatile
        var lastTopRect: Rect? = null
            private set
        @Volatile
        var lastBottomRect: Rect? = null
            private set
        @Volatile
        var lastHybridBottomRect: Rect? = null
            private set
        @Volatile
        var lastSecondaryBottomRect: Rect? = null
            private set
    }

    private var mainSurfaceView: EmulatorSurfaceView? = null
    private var secondarySurfaceView: EmulatorSurfaceView? = null
    @Volatile
    private var stopped = false

    override fun addSurface(surface: EmulatorSurfaceView) {
        if (stopped) return
        if (mainSurfaceView == null || mainSurfaceView === surface) {
            mainSurfaceView = surface
            surface.setSurfaceLifecycleListener(this)
            surface.getCurrentSurface()?.let { s ->
                if (s.isValid) NativeLibrary.surfaceChanged(s)
            }
        } else {
            secondarySurfaceView = surface
            surface.setSurfaceLifecycleListener(this)
            surface.getCurrentSurface()?.let { s ->
                if (s.isValid) NativeLibrary.secondarySurfaceChanged(s)
            }
        }
    }

    override fun removeSurface(surface: EmulatorSurfaceView) {
        surface.setSurfaceLifecycleListener(null)
        if (mainSurfaceView === surface) {
            mainSurfaceView = null
            runCatching { NativeLibrary.surfaceDestroyed() }
        } else if (secondarySurfaceView === surface) {
            secondarySurfaceView = null
            runCatching { NativeLibrary.secondarySurfaceDestroyed() }
        }
    }

    override fun updateSurfacePresentation(
        surface: EmulatorSurfaceView,
        config: VulkanPresentationConfig?,
        background: RuntimeBackground,
    ) {
        if (stopped || config == null) return
        if (surface === secondarySurfaceView) {
            lastSecondaryBottomRect = config.bottomScreenRect ?: config.hybridBottomScreenRect
            return
        }
        if (surface !== mainSurfaceView) return
        val top = config.topScreenRect ?: config.hybridTopScreenRect
        val bot = config.bottomScreenRect ?: config.hybridBottomScreenRect
        lastTopRect = config.topScreenRect
        lastBottomRect = config.bottomScreenRect
        lastHybridBottomRect = config.hybridBottomScreenRect
        val isPortrait = NativeLibrary.isPortraitMode()
        NativeLibrary.setCustomScreenLayout(
            isPortrait = isPortrait,
            topX = top?.x ?: 0,
            topY = top?.y ?: 0,
            topW = (top?.width ?: 0).coerceAtLeast(0),
            topH = (top?.height ?: 0).coerceAtLeast(0),
            botX = bot?.x ?: 0,
            botY = bot?.y ?: 0,
            botW = (bot?.width ?: 0).coerceAtLeast(0),
            botH = (bot?.height ?: 0).coerceAtLeast(0),
            secondLayerOpacity = 100,
        )
    }

    override fun renderFrame(frameDeadlineNanos: Long?) {
        // Libstorm3ds renders asynchronously on Azahar GPU thread directly to ANativeWindow
    }

    override fun stop() {
        stopped = true
        mainSurfaceView?.setSurfaceLifecycleListener(null)
        mainSurfaceView = null
        secondarySurfaceView?.setSurfaceLifecycleListener(null)
        secondarySurfaceView = null
        runCatching { NativeLibrary.secondarySurfaceDestroyed() }
        runCatching { NativeLibrary.surfaceDestroyed() }
    }

    override fun onSurfaceCreated(surfaceView: EmulatorSurfaceView, surface: Surface) {
        if (stopped || !surface.isValid) return
        if (surfaceView === secondarySurfaceView) {
            NativeLibrary.secondarySurfaceChanged(surface)
        } else {
            NativeLibrary.surfaceChanged(surface)
        }
    }

    override fun onSurfaceChanged(surfaceView: EmulatorSurfaceView, surface: Surface, width: Int, height: Int) {
        if (stopped || !surface.isValid) return
        if (surfaceView === secondarySurfaceView) {
            NativeLibrary.secondarySurfaceChanged(surface)
        } else {
            NativeLibrary.surfaceChanged(surface)
        }
    }

    override fun onSurfaceDestroyed(surfaceView: EmulatorSurfaceView) {
        if (stopped) return
        if (surfaceView === secondarySurfaceView) {
            runCatching { NativeLibrary.secondarySurfaceDestroyed() }
        } else {
            runCatching { NativeLibrary.surfaceDestroyed() }
        }
    }
}
