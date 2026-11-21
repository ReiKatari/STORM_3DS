package com.storm3ds.emulator.ui.emulator.render

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.storm3ds.emulator.domain.model.RuntimeBackground
import com.storm3ds.emulator.domain.model.VideoFiltering
import com.storm3ds.emulator.domain.model.layout.LayoutComponent
import com.storm3ds.emulator.domain.model.VideoRenderer
import com.storm3ds.emulator.ui.emulator.DSRenderer
import com.storm3ds.emulator.ui.emulator.EmulatorSurfaceView
import com.storm3ds.emulator.ui.emulator.RuntimeLayoutView
import com.storm3ds.emulator.ui.emulator.model.RuntimeInputLayoutConfiguration
import com.storm3ds.emulator.ui.emulator.model.RuntimeRendererConfiguration
import com.storm3ds.emulator.ui.emulator.model.VulkanPresentationConfig
import com.storm3ds.emulator.ui.layouteditor.model.LayoutTarget
import kotlin.collections.orEmpty
import kotlin.math.max

class ExternalPresentation(
    context: Context,
    display: Display,
    private val frameRenderCoordinator: FrameRenderCoordinator,
    private var excludeTouchScreenFromSystemGestures: Boolean,
) : Presentation(context, display) {
    private data class ScreenPresentationAreas(
        val topScreenRect: com.storm3ds.emulator.domain.model.Rect?,
        val bottomScreenRect: com.storm3ds.emulator.domain.model.Rect?,
        val topAlpha: Float,
        val bottomAlpha: Float,
        val topOnTop: Boolean,
        val bottomOnTop: Boolean,
        val hybridTopScreenRect: com.storm3ds.emulator.domain.model.Rect?,
        val hybridBottomScreenRect: com.storm3ds.emulator.domain.model.Rect?,
        val hybridAlpha: Float,
        val hybridOnTop: Boolean,
    )


    val layoutView = RuntimeLayoutView(context)
    private val container = FrameLayout(context)
    private val pauseOverlay = View(context)
    private val infoOverlayContent = androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)
    private val infoOverlayView = androidx.compose.ui.platform.ComposeView(context)
    private val emulatorRenderer: DSRenderer
    private val surfaceView: EmulatorSurfaceView
    private var currentBackground: RuntimeBackground? = null
    private var currentRendererConfiguration: RuntimeRendererConfiguration? = null

    init {
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )

        val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateRendererScreenAreas()
        }

        container.addOnLayoutChangeListener(layoutChangeListener)
        layoutView.addOnLayoutChangeListener(layoutChangeListener)
        emulatorRenderer = DSRenderer(context).also {
            surfaceView = createSurfaceView(it)
        }
        surfaceView.updateRendererConfiguration(currentRendererConfiguration)

        container.addView(surfaceView)
        container.addView(layoutView)
        container.addView(pauseOverlay)
        container.addView(infoOverlayView)

        infoOverlayView.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        infoOverlayView.setContent {
            com.storm3ds.emulator.ui.theme.MelonTheme(isDarkTheme = true) {
                infoOverlayContent.value?.invoke()
            }
        }

        pauseOverlay.apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0.6f
            isVisible = false
            setOnClickListener {
                // Do nothing. Just intercept clicks
            }
        }

        frameRenderCoordinator.addSurface(surfaceView)

        (context as? LifecycleOwner)?.let { owner ->
            container.setViewTreeLifecycleOwner(owner)
            window?.decorView?.setViewTreeLifecycleOwner(owner)
        }
        (context as? ViewModelStoreOwner)?.let { owner ->
            container.setViewTreeViewModelStoreOwner(owner)
            window?.decorView?.setViewTreeViewModelStoreOwner(owner)
        }
        (context as? SavedStateRegistryOwner)?.let { owner ->
            container.setViewTreeSavedStateRegistryOwner(owner)
            window?.decorView?.setViewTreeSavedStateRegistryOwner(owner)
        }

        setContentView(container)
    }

    fun swapScreens() {
        layoutView.swapScreens()
        updateRendererScreenAreas()
    }

    fun updateRendererScreenAreas() {
        val areas = resolveScreenPresentationAreas()
        emulatorRenderer.updateScreenAreas(
            topScreenRect = areas.topScreenRect,
            bottomScreenRect = areas.bottomScreenRect,
            topAlpha = areas.topAlpha,
            bottomAlpha = areas.bottomAlpha,
            topOnTop = areas.topOnTop,
            bottomOnTop = areas.bottomOnTop,
            hybridTopScreenRect = areas.hybridTopScreenRect,
            hybridBottomScreenRect = areas.hybridBottomScreenRect,
            hybridAlpha = areas.hybridAlpha,
            hybridOnTop = areas.hybridOnTop,
        )

        frameRenderCoordinator.updateSurfacePresentation(
            surfaceView,
            buildVulkanPresentationConfig(
                areas.topScreenRect,
                areas.bottomScreenRect,
                areas.topAlpha,
                areas.bottomAlpha,
                areas.topOnTop,
                areas.bottomOnTop,
                areas.hybridTopScreenRect,
                areas.hybridBottomScreenRect,
                areas.hybridAlpha,
                areas.hybridOnTop,
            ),
            currentBackground ?: RuntimeBackground.None,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && window?.decorView?.isAttachedToWindow == true) {
            val touchScreenArea = if (excludeTouchScreenFromSystemGestures) {
                listOfNotNull(areas.bottomScreenRect, areas.hybridBottomScreenRect).map {
                    android.graphics.Rect(it.x, it.y, it.right, it.bottom)
                }
            } else null
            layoutView.systemGestureExclusionRects = touchScreenArea.orEmpty()
        }
    }

    private fun resolveScreenPresentationAreas(): ScreenPresentationAreas {
        val (topScreen, bottomScreen) = if (layoutView.areScreensSwapped()) {
            LayoutComponent.BOTTOM_SCREEN to LayoutComponent.TOP_SCREEN
        } else {
            LayoutComponent.TOP_SCREEN to LayoutComponent.BOTTOM_SCREEN
        }
        val topView = layoutView.getLayoutComponentView(topScreen)
        val bottomView = layoutView.getLayoutComponentView(bottomScreen)
        val hybridView = layoutView.getLayoutComponentView(LayoutComponent.HYBRID_SCREEN)
        val (hybridTopRect, hybridBottomRect) = hybridView?.let { splitHybridScreenRect(it.getRect()) } ?: (null to null)
        return ScreenPresentationAreas(
            topScreenRect = topView?.getRect(),
            bottomScreenRect = bottomView?.getRect(),
            topAlpha = topView?.baseAlpha ?: 1f,
            bottomAlpha = bottomView?.baseAlpha ?: 1f,
            topOnTop = topView?.onTop ?: false,
            bottomOnTop = bottomView?.onTop ?: false,
            hybridTopScreenRect = hybridTopRect,
            hybridBottomScreenRect = hybridBottomRect,
            hybridAlpha = hybridView?.baseAlpha ?: 1f,
            hybridOnTop = hybridView?.onTop ?: false,
        )
    }

    private fun splitHybridScreenRect(rect: com.storm3ds.emulator.domain.model.Rect): Pair<com.storm3ds.emulator.domain.model.Rect, com.storm3ds.emulator.domain.model.Rect> {
        val topHeight = max(1, rect.height / 2)
        val bottomHeight = max(1, rect.height - topHeight)
        return com.storm3ds.emulator.domain.model.Rect(rect.x, rect.y, rect.width, topHeight) to
                com.storm3ds.emulator.domain.model.Rect(rect.x, rect.y + topHeight, rect.width, bottomHeight)
    }

    fun setTouchScreenSystemGestureExclusionEnabled(enabled: Boolean) {
        excludeTouchScreenFromSystemGestures = enabled
        updateRendererScreenAreas()
    }

    fun setInfoOverlayContent(content: (@androidx.compose.runtime.Composable () -> Unit)?) {
        infoOverlayContent.value = content
    }

    fun setPauseOverlayVisibility(visible: Boolean) {
        pauseOverlay.isVisible = visible
    }

    private fun createSurfaceView(renderer: EmulatorRenderer): EmulatorSurfaceView {
        return EmulatorSurfaceView(context).apply {
            setRenderer(renderer)
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    fun updateLayout(layoutConfiguration: RuntimeInputLayoutConfiguration) {
        layoutView.instantiateLayout(layoutConfiguration, LayoutTarget.SECONDARY_SCREEN)
        updateRendererScreenAreas()
    }

    fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
        currentRendererConfiguration = newRendererConfiguration
        surfaceView.updateRendererConfiguration(newRendererConfiguration)
        updateRendererScreenAreas()
    }

    override fun onStart() {
        super.onStart()
        layoutView.post {
            updateRendererScreenAreas()
        }
    }

    override fun onStop() {
        super.onStop()
        frameRenderCoordinator.removeSurface(surfaceView)
        release()
    }

    fun release() {
        emulatorRenderer.release()
    }

    fun updateBackground(background: RuntimeBackground) {
        currentBackground = background
        emulatorRenderer.setBackground(background)
        updateRendererScreenAreas()
    }

    private fun buildVulkanPresentationConfig(
        topScreenRect: com.storm3ds.emulator.domain.model.Rect?,
        bottomScreenRect: com.storm3ds.emulator.domain.model.Rect?,
        topAlpha: Float,
        bottomAlpha: Float,
        topOnTop: Boolean,
        bottomOnTop: Boolean,
        hybridTopScreenRect: com.storm3ds.emulator.domain.model.Rect?,
        hybridBottomScreenRect: com.storm3ds.emulator.domain.model.Rect?,
        hybridAlpha: Float,
        hybridOnTop: Boolean,
    ): VulkanPresentationConfig? {
        val rendererConfiguration = currentRendererConfiguration ?: return null
        if (rendererConfiguration.renderer != VideoRenderer.VULKAN) {
            return null
        }

        val (surfaceWidth, surfaceHeight) = surfaceView.getCurrentSurfaceSize()
        val (resolvedTopScreenRect, resolvedBottomScreenRect) = resolveVulkanScreenRects(
            topScreenRect = topScreenRect,
            bottomScreenRect = bottomScreenRect,
            surfaceWidth = if (surfaceWidth > 0) surfaceWidth else surfaceView.width,
            surfaceHeight = if (surfaceHeight > 0) surfaceHeight else surfaceView.height,
        )

        return VulkanPresentationConfig(
            topScreenRect = resolvedTopScreenRect,
            bottomScreenRect = resolvedBottomScreenRect,
            topAlpha = topAlpha,
            bottomAlpha = bottomAlpha,
            topOnTop = topOnTop,
            bottomOnTop = bottomOnTop,
            hybridTopScreenRect = hybridTopScreenRect?.takeIf { it.width > 0 && it.height > 0 },
            hybridBottomScreenRect = hybridBottomScreenRect?.takeIf { it.width > 0 && it.height > 0 },
            hybridAlpha = hybridAlpha,
            hybridOnTop = hybridOnTop,
            backgroundMode = currentBackground?.mode ?: RuntimeBackground.None.mode,
            videoFiltering = rendererConfiguration.videoFiltering,
            retroShaderEnabled = rendererConfiguration.videoFiltering == VideoFiltering.RETROARCH,
            retroShaderPresetPath = rendererConfiguration.retroArchShader.presetPath,
            retroShaderSourceResolution = rendererConfiguration.retroArchShader.sourceResolution.name.lowercase(),
            retroShaderPassCount = rendererConfiguration.retroArchShader.passCount,
            retroShaderParameterOverrides = rendererConfiguration.retroArchShader.parameterOverrides,
            retroShaderClearHistory = rendererConfiguration.retroArchShader.clearHistory,
        )
    }

    private fun resolveVulkanScreenRects(
        topScreenRect: com.storm3ds.emulator.domain.model.Rect?,
        bottomScreenRect: com.storm3ds.emulator.domain.model.Rect?,
        surfaceWidth: Int,
        surfaceHeight: Int,
    ): Pair<com.storm3ds.emulator.domain.model.Rect?, com.storm3ds.emulator.domain.model.Rect?> {
        val sanitizedTopRect = topScreenRect?.takeIf { it.width > 0 && it.height > 0 }
        val sanitizedBottomRect = bottomScreenRect?.takeIf { it.width > 0 && it.height > 0 }
        if (surfaceWidth <= 0 || surfaceHeight <= 0)
            return null to null
        return sanitizedTopRect to sanitizedBottomRect
    }
}
