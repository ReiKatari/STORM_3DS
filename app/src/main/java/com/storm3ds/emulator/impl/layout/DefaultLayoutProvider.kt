package com.storm3ds.emulator.impl.layout

import android.content.Context
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.storm3ds.emulator.domain.model.Rect
import com.storm3ds.emulator.domain.model.SCREEN_HEIGHT
import com.storm3ds.emulator.domain.model.SCREEN_WIDTH
import com.storm3ds.emulator.domain.model.ThreeDS
import com.storm3ds.emulator.domain.model.consoleAspectRatio
import com.storm3ds.emulator.domain.model.layout.Insets
import com.storm3ds.emulator.domain.model.layout.LayoutComponent
import com.storm3ds.emulator.domain.model.layout.LayoutConfiguration
import com.storm3ds.emulator.domain.model.layout.LayoutDisplay
import com.storm3ds.emulator.domain.model.layout.PositionedLayoutComponent
import com.storm3ds.emulator.domain.model.layout.ScreenFold
import com.storm3ds.emulator.domain.model.layout.ScreenLayout
import com.storm3ds.emulator.domain.model.layout.UILayout
import com.storm3ds.emulator.domain.model.layout.UILayoutVariant
import com.storm3ds.emulator.domain.model.ui.Orientation
import com.storm3ds.emulator.impl.ScreenUnitsConverter
import kotlin.math.max
import kotlin.math.min

class DefaultLayoutProvider(
    private val context: Context,
    private val screenUnitsConverter: ScreenUnitsConverter,
) {

    private fun dp(value: Float): Int = screenUnitsConverter.dpToPixels(value).toInt()

    private fun isConsoleSkinEnabled(): Boolean = runCatching {
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("video_console_skin_enabled", false)
    }.getOrDefault(false)

    private fun effectiveInsets(insets: Insets): Insets {
        val minBottom = dp(4f)
        val minTop = dp(8f)
        val minSide = dp(10f)
        return Insets(
            left = max(insets.left, minSide),
            top = max(insets.top, minTop),
            right = max(insets.right, minSide),
            bottom = max(insets.bottom, minBottom),
        )
    }

    private fun centeredStrip(
        centerX: Int,
        y: Int,
        itemSize: Int,
        gap: Int,
        components: List<LayoutComponent>,
    ): List<PositionedLayoutComponent> {
        if (components.isEmpty()) return emptyList()
        val totalWidth = components.size * itemSize + (components.size - 1) * gap
        val startX = centerX - totalWidth / 2
        return components.mapIndexed { index, component ->
            PositionedLayoutComponent(
                Rect(startX + index * (itemSize + gap), y, itemSize, itemSize),
                component,
            )
        }
    }

    /**
     * Builds ergonomic New 3DS portrait controls below [deckTopY] down to [bottomAnchor].
     */
    private fun buildPortraitControls(
        width: Int,
        safeLeft: Int,
        safeRight: Int,
        deckTopY: Int,
        bottomAnchor: Int,
        skinEnabled: Boolean = false,
    ): List<PositionedLayoutComponent> {
        val availableHeight = (bottomAnchor - deckTopY).coerceAtLeast(dp(200f))
        val availableWidth = (width - safeLeft - safeRight).coerceAtLeast(dp(300f))
        val narrow = availableWidth < dp(380f)
        val compact = availableHeight < dp(290f)

        val dpadSize = dp(if (narrow || compact) 106f else 114f)
        val circlePadSize = dp(if (compact) 88f else 98f)
        val faceButtonsSize = dp(if (narrow || compact) 114f else 122f)
        val cStickSize = dp(if (compact) 48f else 54f)

        val lrWidth = dp(if (narrow) 48f else 54f)
        val lrHeight = dp(32f)
        val zlWidth = dp(if (narrow) 40f else 44f)
        val zlHeight = dp(28f)

        val selectStartWidth = dp(if (narrow) 44f else 48f)
        val selectStartHeight = dp(32f)
        val homeW = dp(38f)
        val homeH = dp(30f)
        val utilSize = dp(if (narrow || compact) 30f else 32f)
        val utilGap = dp(if (narrow) 4f else 5f)
        val centerX = safeLeft + availableWidth / 2

        val controls = mutableListOf<PositionedLayoutComponent>()

        // 1. Bottom tier flush to bottomAnchor (STORM DS style): DPAD, ABXY, SELECT, START, HOME
        val dpadY = bottomAnchor - dpadSize
        val faceButtonsY = bottomAnchor - faceButtonsSize
        val selectStartY = bottomAnchor - selectStartHeight
        val startStopGap = dp(6f)
        val homeY = selectStartY - homeH - dp(6f)

        // 2. Sticks cleanly above D-Pad / ABXY
        val baseCirclePadY = dpadY - circlePadSize - dp(6f)
        val baseCStickY = faceButtonsY - cStickSize - dp(8f)
        val sticksTopY = min(baseCirclePadY, baseCStickY)

        // 3. Dedicated L/ZL and ZR/R shoulder row cleanly below hotkeys & above sticks (no hotkey overlap)
        val minSkinY = deckTopY + (if (skinEnabled) dp(30f) else dp(8f))
        val desiredShoulderY = sticksTopY - lrHeight - dp(8f)
        val desiredExtraBarY = desiredShoulderY - utilSize - dp(6f)
        val desiredCoreBarY = desiredExtraBarY - utilSize - dp(5f)

        val coreBarY = max(desiredCoreBarY, minSkinY)
        val extraBarY = coreBarY + utilSize + (if (desiredCoreBarY >= minSkinY) dp(5f) else dp(4f))
        val shoulderY = max(desiredShoulderY, extraBarY + utilSize + dp(5f))

        val coreStrip = listOf(
            LayoutComponent.BUTTON_PAUSE,
            LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE,
            LayoutComponent.BUTTON_SWAP_SCREENS,
            LayoutComponent.BUTTON_TOGGLE_EXTRA_BUTTONS,
        )
        controls += centeredStrip(centerX, coreBarY, utilSize, utilGap, coreStrip)

        val extraStrip = listOf(
            LayoutComponent.BUTTON_QUICK_SAVE,
            LayoutComponent.BUTTON_QUICK_LOAD,
            LayoutComponent.BUTTON_MICROPHONE_TOGGLE,
            LayoutComponent.BUTTON_HINGE,
            LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT,
        )
        controls += centeredStrip(centerX, extraBarY, utilSize, utilGap, extraStrip)

        val triggerGap = dp(4f)
        controls += PositionedLayoutComponent(Rect(safeLeft, shoulderY, lrWidth, lrHeight), LayoutComponent.BUTTON_L)
        controls += PositionedLayoutComponent(Rect(safeLeft + lrWidth + triggerGap, shoulderY + (lrHeight - zlHeight) / 2, zlWidth, zlHeight), LayoutComponent.BUTTON_ZL)

        val rightRLeft = width - safeRight - lrWidth
        controls += PositionedLayoutComponent(Rect(rightRLeft, shoulderY, lrWidth, lrHeight), LayoutComponent.BUTTON_R)
        controls += PositionedLayoutComponent(Rect(rightRLeft - triggerGap - zlWidth, shoulderY + (lrHeight - zlHeight) / 2, zlWidth, zlHeight), LayoutComponent.BUTTON_ZR)

        val minStickY = shoulderY + lrHeight + dp(4f)
        val circlePadY = baseCirclePadY.coerceAtLeast(minStickY).coerceAtMost(dpadY - dp(4f))
        val circlePadX = safeLeft + (dpadSize - circlePadSize) / 2
        controls += PositionedLayoutComponent(Rect(circlePadX, circlePadY, circlePadSize, circlePadSize), LayoutComponent.CIRCLE_PAD)
        controls += PositionedLayoutComponent(Rect(safeLeft, dpadY, dpadSize, dpadSize), LayoutComponent.DPAD)

        val faceButtonsX = width - safeRight - faceButtonsSize
        val cStickX = (faceButtonsX - cStickSize / 2 - dp(4f)).coerceAtLeast(centerX + dp(18f))
        val cStickY = baseCStickY.coerceAtLeast(minStickY).coerceAtMost(faceButtonsY - dp(4f))
        controls += PositionedLayoutComponent(Rect(cStickX, cStickY, cStickSize, cStickSize), LayoutComponent.C_STICK)
        controls += PositionedLayoutComponent(Rect(faceButtonsX, faceButtonsY, faceButtonsSize, faceButtonsSize), LayoutComponent.BUTTONS)

        controls += PositionedLayoutComponent(
            Rect(centerX - selectStartWidth - startStopGap / 2, selectStartY, selectStartWidth, selectStartHeight),
            LayoutComponent.BUTTON_SELECT,
        )
        controls += PositionedLayoutComponent(
            Rect(centerX + startStopGap / 2, selectStartY, selectStartWidth, selectStartHeight),
            LayoutComponent.BUTTON_START,
        )
        controls += PositionedLayoutComponent(
            Rect(centerX - homeW / 2, homeY.coerceAtLeast(minStickY), homeW, homeH),
            LayoutComponent.BUTTON_HOME,
        )

        return controls
    }

    /**
     * Builds ergonomic Switch/Backbone-style side-rail landscape controls within [safeInsets].
     */
    private fun buildLandscapeControls(
        width: Int,
        height: Int,
        safeInsets: Insets,
        bottomSystemCenterX: Int? = null,
    ): List<PositionedLayoutComponent> {
        val safeLeft = safeInsets.left
        val safeTop = safeInsets.top
        val safeRight = safeInsets.right
        val safeBottom = safeInsets.bottom
        val centerX = safeLeft + (width - safeLeft - safeRight) / 2
        val bottomAnchor = height - safeBottom

        val compactHeight = (bottomAnchor - safeTop) < dp(350f)
        val dpadSize = dp(if (compactHeight) 100f else 108f)
        val circlePadSize = dp(if (compactHeight) 86f else 94f)
        val faceButtonsSize = dp(if (compactHeight) 108f else 116f)
        val cStickSize = dp(if (compactHeight) 46f else 52f)

        val lrWidth = dp(54f)
        val lrHeight = dp(34f)
        val zlWidth = dp(42f)
        val zlHeight = dp(30f)
        val utilSize = dp(30f)
        val utilGap = dp(5f)
        val trigGap = dp(4f)

        val controls = mutableListOf<PositionedLayoutComponent>()

        // Upper shoulder triggers: L + ZL fit inside left rail (98dp), ZR + R fit inside right rail (98dp)
        controls += PositionedLayoutComponent(Rect(safeLeft, safeTop, lrWidth, lrHeight), LayoutComponent.BUTTON_L)
        controls += PositionedLayoutComponent(Rect(safeLeft + lrWidth + trigGap, safeTop + (lrHeight - zlHeight) / 2, zlWidth, zlHeight), LayoutComponent.BUTTON_ZL)

        val rLeft = width - safeRight - lrWidth
        controls += PositionedLayoutComponent(Rect(rLeft, safeTop, lrWidth, lrHeight), LayoutComponent.BUTTON_R)
        controls += PositionedLayoutComponent(Rect(rLeft - trigGap - zlWidth, safeTop + (lrHeight - zlHeight) / 2, zlWidth, zlHeight), LayoutComponent.BUTTON_ZR)

        // Compact top utility bar within topReserve (38dp), secondary strip tucked below when toggled on
        val coreStrip = listOf(
            LayoutComponent.BUTTON_PAUSE,
            LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE,
            LayoutComponent.BUTTON_SWAP_SCREENS,
            LayoutComponent.BUTTON_TOGGLE_EXTRA_BUTTONS,
        )
        controls += centeredStrip(centerX, safeTop, utilSize, utilGap, coreStrip)

        val extraStrip = listOf(
            LayoutComponent.BUTTON_QUICK_SAVE,
            LayoutComponent.BUTTON_QUICK_LOAD,
            LayoutComponent.BUTTON_MICROPHONE_TOGGLE,
            LayoutComponent.BUTTON_HINGE,
            LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT,
        )
        controls += centeredStrip(centerX, safeTop + utilSize + dp(4f), utilSize, utilGap, extraStrip)

        // Left grip rail: Circle Pad above D-Pad (stays inside leftRail = 114dp)
        val dpadY = bottomAnchor - dpadSize
        val circlePadY = (dpadY - circlePadSize - dp(8f)).coerceAtLeast(safeTop + lrHeight + dp(6f))
        val circlePadX = safeLeft + (dpadSize - circlePadSize) / 2
        controls += PositionedLayoutComponent(Rect(circlePadX, circlePadY, circlePadSize, circlePadSize), LayoutComponent.CIRCLE_PAD)
        controls += PositionedLayoutComponent(Rect(safeLeft, dpadY, dpadSize, dpadSize), LayoutComponent.DPAD)

        // Right grip rail: ABXY + C-Stick kept inside rightRail (122dp) so gameplay screen stays uncovered
        val faceButtonsX = width - safeRight - faceButtonsSize
        val faceButtonsY = bottomAnchor - faceButtonsSize
        val rightRailMinX = width - safeRight - dp(122f)
        val cStickX = (faceButtonsX - cStickSize / 3).coerceAtLeast(rightRailMinX)
        val cStickY = (faceButtonsY - cStickSize - dp(8f)).coerceAtLeast(safeTop + lrHeight + dp(6f))
        controls += PositionedLayoutComponent(Rect(cStickX, cStickY, cStickSize, cStickSize), LayoutComponent.C_STICK)
        controls += PositionedLayoutComponent(Rect(faceButtonsX, faceButtonsY, faceButtonsSize, faceButtonsSize), LayoutComponent.BUTTONS)

        // Bottom row: SELECT - HOME - START (placed under bottom screen pocket in uneven landscape when provided)
        val selectStartWidth = dp(46f)
        val selectStartHeight = dp(30f)
        val homeSize = dp(32f)
        val bottomGap = dp(6f)
        val bottomGroupWidth = selectStartWidth * 2 + homeSize + bottomGap * 2
        val targetBottomCenterX = (bottomSystemCenterX ?: centerX).coerceIn(
            safeLeft + bottomGroupWidth / 2,
            width - safeRight - bottomGroupWidth / 2,
        )
        val bottomStartX = targetBottomCenterX - bottomGroupWidth / 2
        val bottomRowY = bottomAnchor - selectStartHeight
        controls += PositionedLayoutComponent(Rect(bottomStartX, bottomRowY, selectStartWidth, selectStartHeight), LayoutComponent.BUTTON_SELECT)
        controls += PositionedLayoutComponent(Rect(bottomStartX + selectStartWidth + bottomGap, bottomRowY, homeSize, homeSize), LayoutComponent.BUTTON_HOME)
        controls += PositionedLayoutComponent(Rect(bottomStartX + selectStartWidth + homeSize + bottomGap * 2, bottomRowY, selectStartWidth, selectStartHeight), LayoutComponent.BUTTON_START)

        return controls
    }

    fun buildDefaultLayout(variant: UILayoutVariant, layoutId: java.util.UUID? = null): UILayout {
        val width = variant.uiSize.x
        val height = variant.uiSize.y
        val orientation = variant.orientation
        val folds = variant.folds
        val mainDisplay = variant.displays.mainScreenDisplay
        val secondaryDisplay = variant.displays.secondaryScreenDisplay
        val mainDisplayInsets = variant.uiInsets

        if (secondaryDisplay == null) {
            if (orientation == Orientation.LANDSCAPE) {
                if (layoutId == LayoutConfiguration.UNEVEN_LANDSCAPE_ID) {
                    return UILayout(buildUnevenLandscapeLayout(width, height, mainDisplayInsets), ScreenLayout())
                } else if (layoutId == LayoutConfiguration.PROPORTIONAL_LANDSCAPE_ID) {
                    return UILayout(buildProportionalLandscapeLayout(width, height, mainDisplayInsets), ScreenLayout())
                } else if (layoutId == LayoutConfiguration.FULLSCREEN_LANDSCAPE_ID) {
                    return UILayout(buildFullscreenLandscapeLayout(width, height, mainDisplayInsets), ScreenLayout())
                }
            } else if (orientation == Orientation.PORTRAIT) {
                if (layoutId == LayoutConfiguration.EVEN_PORTRAIT_ID) {
                    return UILayout(buildDefaultPortraitLayout(width, height, mainDisplayInsets), ScreenLayout())
                }
            }
        }

        val (mainScreenLayout, secondaryScreenLayout) = if (secondaryDisplay != null) {
            val secondaryDisplayInsets = getDisplaySafeInsets(secondaryDisplay)

            if (mainDisplay.type == LayoutDisplay.Type.BUILT_IN && secondaryDisplay.type == LayoutDisplay.Type.BUILT_IN) {
                if (mainDisplay.isDefaultDisplay) {
                    val secondaryDisplayOrientation = if (secondaryDisplay.width > secondaryDisplay.height) Orientation.LANDSCAPE else Orientation.PORTRAIT
                    val secondaryLayout = when (secondaryDisplayOrientation) {
                        Orientation.PORTRAIT -> buildDefaultPortraitLayout(secondaryDisplay.width, secondaryDisplay.height, secondaryDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                        Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(secondaryDisplay.width, secondaryDisplay.height, secondaryDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                    }
                    buildSingleScreenLayout(width, height, LayoutComponent.TOP_SCREEN) to secondaryLayout
                } else {
                    val mainLayout = when (orientation) {
                        Orientation.PORTRAIT -> buildDefaultPortraitLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                        Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                    }
                    mainLayout to buildSingleScreenLayout(secondaryDisplay.width, secondaryDisplay.height, LayoutComponent.TOP_SCREEN)
                }
            } else {
                val mainLayout = when (orientation) {
                    Orientation.PORTRAIT -> buildDefaultPortraitLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                    Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                }
                mainLayout to buildSingleScreenLayout(secondaryDisplay.width, secondaryDisplay.height, LayoutComponent.TOP_SCREEN)
            }
        } else {
            val mainScreenLayout = when (orientation) {
                Orientation.PORTRAIT -> {
                    if (folds.any { it.orientation == Orientation.LANDSCAPE }) {
                        buildDefaultFoldingPortraitLayout(width, height, folds, mainDisplayInsets)
                    } else {
                        buildDefaultPortraitLayout(width, height, mainDisplayInsets)
                    }
                }
                Orientation.LANDSCAPE -> {
                    if (folds.any { it.orientation == Orientation.PORTRAIT }) {
                        buildDefaultFoldingLandscapeLayout(width, height, folds, mainDisplayInsets)
                    } else {
                        buildDefaultLandscapeLayout(width, height, mainDisplayInsets)
                    }
                }
            }
            mainScreenLayout to ScreenLayout()
        }

        return UILayout(mainScreenLayout, secondaryScreenLayout)
    }

    private fun buildDefaultPortraitLayout(
        width: Int,
        height: Int,
        insets: Insets,
        singleScreenComponent: LayoutComponent? = null,
    ): ScreenLayout {
        require(singleScreenComponent?.isScreen() != false) { "When specifying a single screen component, it must be a screen component" }

        val skinEnabled = isConsoleSkinEnabled()
        val safe = effectiveInsets(insets)
        val skinSideInset = if (skinEnabled) dp(6f) else 0
        val safeLeft = safe.left + skinSideInset
        val safeRight = safe.right + skinSideInset
        val safeTop = if (skinEnabled) max(safe.top, dp(28f)) else safe.top
        val safeBottom = safe.bottom
        val safeWidth = (width - safeLeft - safeRight).coerceAtLeast(120)
        val safeHeight = (height - safeTop - safeBottom).coerceAtLeast(240)
        val skinHingeGap = if (skinEnabled && singleScreenComponent == null) dp(20f) else 0

        // Reserve ergonomic lower deck height + 3D skin hinge/pill-bar clearance
        val minDeckHeight = dp(if (skinEnabled) 356f else 334f)
        val minScreensTotalHeight = (safeHeight * 0.44f).toInt()
        val maxScreenBudget = (safeHeight - minDeckHeight - skinHingeGap).coerceAtLeast(minScreensTotalHeight)

        var topHeight = (safeWidth / ThreeDS.TOP_SCREEN_ASPECT_RATIO).toInt()
        var topWidth = safeWidth
        var bottomWidth = (topHeight * ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO).toInt().coerceAtMost(safeWidth)
        var bottomHeight = (bottomWidth / ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO).toInt()

        val screenComponents = if (singleScreenComponent == null) {
            val totalRawHeight = topHeight + bottomHeight
            if (totalRawHeight > maxScreenBudget && totalRawHeight > 0) {
                val scale = maxScreenBudget.toFloat() / totalRawHeight.toFloat()
                topHeight = (topHeight * scale).toInt().coerceAtLeast(60)
                topWidth = (topHeight * ThreeDS.TOP_SCREEN_ASPECT_RATIO).toInt().coerceAtMost(safeWidth)
                bottomHeight = (maxScreenBudget - topHeight).coerceAtLeast(60)
                bottomWidth = (bottomHeight * ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO).toInt().coerceAtMost(safeWidth)
                bottomHeight = (bottomWidth / ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO).toInt().coerceAtMost(bottomHeight)
            }
            val topX = safeLeft + ((safeWidth - topWidth) / 2).coerceAtLeast(0)
            val topScreenView = Rect(topX, safeTop, topWidth, topHeight)
            val bottomX = safeLeft + ((safeWidth - bottomWidth) / 2).coerceAtLeast(0)
            val bottomScreenView = Rect(bottomX, safeTop + topHeight + skinHingeGap, bottomWidth, bottomHeight)

            arrayOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
            )
        } else {
            val ratio = if (singleScreenComponent == LayoutComponent.BOTTOM_SCREEN) ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO else ThreeDS.TOP_SCREEN_ASPECT_RATIO
            var singleW = safeWidth
            var singleH = (singleW / ratio).toInt()
            if (singleH > maxScreenBudget) {
                singleH = maxScreenBudget
                singleW = (singleH * ratio).toInt().coerceAtMost(safeWidth)
            }
            val screenArea = Rect(safeLeft + (safeWidth - singleW) / 2, safeTop, singleW, singleH)
            topHeight = singleH
            bottomHeight = 0
            arrayOf(PositionedLayoutComponent(screenArea, singleScreenComponent))
        }

        val screensBottom = if (singleScreenComponent == null) {
            safeTop + topHeight + skinHingeGap + bottomHeight
        } else {
            safeTop + topHeight
        }
        val bottomAnchor = height - safeBottom
        val controls = buildPortraitControls(
            width = width,
            safeLeft = safeLeft,
            safeRight = safeRight,
            deckTopY = screensBottom,
            bottomAnchor = bottomAnchor,
            skinEnabled = skinEnabled,
        )

        return ScreenLayout(listOf(*screenComponents) + controls)
    }

    private fun buildDefaultLandscapeLayout(
        width: Int,
        height: Int,
        insets: Insets,
        singleScreenComponent: LayoutComponent? = null,
    ): ScreenLayout {
        require(singleScreenComponent?.isScreen() != false) { "When specifying a single screen component, it must be a screen component" }

        val safe = effectiveInsets(insets)
        val safeLeft = safe.left
        val safeTop = safe.top
        val safeRight = safe.right
        val safeBottom = safe.bottom
        val safeWidth = (width - safeLeft - safeRight).coerceAtLeast(240)
        val safeHeight = (height - safeTop - safeBottom).coerceAtLeast(180)

        // Dedicated side grip rails (114dp left / 122dp right) so screens stay cleanly inside middle window
        val leftRail = if (safeWidth >= dp(480f)) dp(114f) else 0
        val rightRail = if (safeWidth >= dp(480f)) dp(122f) else 0
        val railWidth = leftRail
        val centerAreaWidth = (safeWidth - leftRail - rightRail).coerceAtLeast(dp(240f))
        val topBarReserve = dp(38f)
        val bottomBarReserve = dp(38f)
        val centerAreaHeight = (safeHeight - topBarReserve - bottomBarReserve).coerceAtLeast((safeHeight * 0.68f).toInt())
        val centerAreaTop = safeTop + topBarReserve

        val screenComponents = if (singleScreenComponent == null) {
            var screenHeight = centerAreaHeight
            var topWidth = (screenHeight * ThreeDS.TOP_SCREEN_ASPECT_RATIO).toInt()
            var bottomWidth = (screenHeight * ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO).toInt()
            val totalRatio = ThreeDS.TOP_SCREEN_ASPECT_RATIO + ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO
            if (topWidth + bottomWidth > centerAreaWidth) {
                screenHeight = (centerAreaWidth / totalRatio).toInt().coerceAtLeast(60)
                topWidth = (screenHeight * ThreeDS.TOP_SCREEN_ASPECT_RATIO).toInt()
                bottomWidth = (screenHeight * ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO).toInt()
            }
            val combinedW = topWidth + bottomWidth
            val alignedLeft = safeLeft + railWidth + ((centerAreaWidth - combinedW) / 2).coerceAtLeast(0)
            val alignedTop = centerAreaTop + ((centerAreaHeight - screenHeight) / 2).coerceAtLeast(0)
            val topScreenView = Rect(alignedLeft, alignedTop, topWidth, screenHeight)
            val bottomScreenView = Rect(alignedLeft + topWidth, alignedTop, bottomWidth, screenHeight)

            arrayOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
            )
        } else {
            val screenArea = centerScreenIn(centerAreaWidth, centerAreaHeight)
            val offsetScreen = Rect(
                safeLeft + railWidth + screenArea.x,
                centerAreaTop + screenArea.y,
                screenArea.width,
                screenArea.height,
            )
            arrayOf(PositionedLayoutComponent(offsetScreen, singleScreenComponent))
        }

        val controls = buildLandscapeControls(width, height, safe)
        return ScreenLayout(listOf(*screenComponents) + controls)
    }

    private fun buildDefaultFoldingPortraitLayout(width: Int, height: Int, folds: List<ScreenFold>, insets: Insets): ScreenLayout {
        val mainFold = folds.first()
        val safe = effectiveInsets(insets)
        val safeLeft = safe.left
        val safeTop = safe.top
        val safeRight = safe.right
        val safeBottom = safe.bottom
        val safeWidth = (width - safeLeft - safeRight).coerceAtLeast(120)

        var screenWidth = safeWidth
        var screenHeight = (safeWidth / consoleAspectRatio).toInt()
        val topHalfHeight = (mainFold.foldBounds.y - safeTop).coerceAtLeast(60)
        val bottomHalfHeight = (height - safeBottom - mainFold.foldBounds.bottom).coerceAtLeast(60)
        var screenMargin = 0
        if (screenHeight > topHalfHeight || screenHeight > bottomHalfHeight) {
            val limitingHeight = min(topHalfHeight, bottomHalfHeight)
            screenWidth = (limitingHeight * consoleAspectRatio).toInt()
            screenHeight = limitingHeight
            screenMargin = (safeWidth - screenWidth) / 2
        }

        val topScreenView = Rect(safeLeft + screenMargin, mainFold.foldBounds.y - screenHeight, screenWidth, screenHeight)
        val foldBottomW = (screenHeight * ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO).toInt().coerceAtMost(safeWidth)
        val foldBottomH = (foldBottomW / ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO).toInt().coerceAtMost(screenHeight)
        val bottomScreenView = Rect(safeLeft + (safeWidth - foldBottomW) / 2, mainFold.foldBounds.bottom, foldBottomW, foldBottomH)

        val deckTop = (bottomScreenView.y + bottomScreenView.height).coerceAtMost(height - safeBottom - dp(210f))
        val controls = buildPortraitControls(
            width = width,
            safeLeft = safeLeft,
            safeRight = safeRight,
            deckTopY = max(mainFold.foldBounds.bottom, deckTop),
            bottomAnchor = height - safeBottom,
            skinEnabled = isConsoleSkinEnabled(),
        )

        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
            ) + controls
        )
    }

    private fun buildDefaultFoldingLandscapeLayout(width: Int, height: Int, folds: List<ScreenFold>, insets: Insets): ScreenLayout {
        val mainFold = folds.first()
        val safe = effectiveInsets(insets)
        val safeLeft = safe.left
        val safeTop = safe.top
        val safeRight = safe.right
        val safeBottom = safe.bottom
        val safeHeight = (height - safeTop - safeBottom).coerceAtLeast(120)

        var topScreenWidth = (mainFold.foldBounds.x - safeLeft).coerceAtLeast(60)
        var topScreenHeight = (topScreenWidth / consoleAspectRatio).toInt()
        if (topScreenHeight > safeHeight) {
            topScreenHeight = safeHeight
            topScreenWidth = (safeHeight * consoleAspectRatio).toInt()
        }

        var bottomScreenWidth = (width - safeRight - mainFold.foldBounds.right).coerceAtLeast(60)
        var bottomScreenHeight = (bottomScreenWidth / consoleAspectRatio).toInt()
        if (bottomScreenHeight > safeHeight) {
            bottomScreenHeight = safeHeight
            bottomScreenWidth = (safeHeight * consoleAspectRatio).toInt()
        }

        val topReserve = dp(44f)
        val screenYPos = if (topScreenHeight < safeHeight - topReserve && bottomScreenHeight < safeHeight - topReserve) {
            safeTop + topReserve
        } else {
            safeTop
        }

        val topScreenView = Rect(mainFold.foldBounds.x - topScreenWidth, screenYPos, topScreenWidth, topScreenHeight)
        val bottomScreenView = Rect(mainFold.foldBounds.right, screenYPos, bottomScreenWidth, bottomScreenHeight)
        val controls = buildLandscapeControls(width, height, safe)

        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
            ) + controls
        )
    }

    private fun buildSingleScreenLayout(width: Int, height: Int, screenComponent: LayoutComponent): ScreenLayout {
        val screenView = centerScreenIn(width, height)
        val positionedScreenComponent = PositionedLayoutComponent(screenView, screenComponent)
        return ScreenLayout(listOf(positionedScreenComponent))
    }

    private fun centerScreenIn(width: Int, height: Int): Rect {
        val areaAspectRatio = width.toFloat() / height
        return if (areaAspectRatio > consoleAspectRatio) {
            val scale = height.toFloat() / SCREEN_HEIGHT
            val scaledWidth = (SCREEN_WIDTH * scale).toInt()
            Rect((width - scaledWidth) / 2, 0, scaledWidth, height)
        } else {
            val scale = width.toFloat() / SCREEN_WIDTH
            val scaledHeight = (SCREEN_HEIGHT * scale).toInt()
            Rect(0, (height - scaledHeight) / 2, width, scaledHeight)
        }
    }

    private fun getDisplaySafeInsets(display: LayoutDisplay): Insets {
        if (!display.isDefaultDisplay) {
            return Insets.Zero
        }

        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean("system_ignore_display_cutout_in_layouts", false)) {
            return Insets.Zero
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Insets.Zero
        }

        val windowManager = context.getSystemService<WindowManager>() ?: return Insets.Zero

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cutoutInsets = windowManager.currentWindowMetrics.windowInsets.getInsets(
                WindowInsets.Type.displayCutout() or WindowInsets.Type.navigationBars()
            )
            Insets(cutoutInsets.left, cutoutInsets.top, cutoutInsets.right, cutoutInsets.bottom)
        } else {
            @Suppress("DEPRECATION")
            val displayCutout = windowManager.defaultDisplay.cutout ?: return Insets.Zero
            Insets(displayCutout.safeInsetLeft, displayCutout.safeInsetTop, displayCutout.safeInsetRight, displayCutout.safeInsetBottom)
        }
    }

    fun buildUnevenLandscapeLayout(width: Int, height: Int, insets: Insets): ScreenLayout {
        val safe = effectiveInsets(insets)
        val safeLeft = safe.left
        val safeTop = safe.top
        val safeRight = safe.right
        val safeBottom = safe.bottom
        val safeWidth = (width - safeLeft - safeRight).coerceAtLeast(240)
        val safeHeight = (height - safeTop - safeBottom).coerceAtLeast(180)

        // Keep both screens inside the clear middle window between the left & right grip rails
        val leftRail = if (safeWidth >= dp(480f)) dp(114f) else 0
        val rightRail = if (safeWidth >= dp(480f)) dp(122f) else 0
        val centerAreaWidth = (safeWidth - leftRail - rightRail).coerceAtLeast(dp(240f))
        val topReserve = dp(38f)
        val bottomReserve = dp(6f)
        val centerAreaHeight = (safeHeight - topReserve - bottomReserve).coerceAtLeast((safeHeight * 0.72f).toInt())
        val centerAreaTop = safeTop + topReserve
        val gapBetweenScreens = dp(8f)

        var mainW = (centerAreaHeight * ThreeDS.TOP_SCREEN_ASPECT_RATIO).toInt()
        var mainH = centerAreaHeight
        val maxMainW = (centerAreaWidth * 0.64f).toInt()
        if (mainW > maxMainW) {
            mainW = maxMainW
            mainH = (mainW / ThreeDS.TOP_SCREEN_ASPECT_RATIO).toInt()
        }

        val subMaxW = (centerAreaWidth - mainW - gapBetweenScreens).coerceAtLeast(dp(84f))
        val desiredSubW = (mainH * 0.62f * ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO).toInt().coerceAtLeast(dp(84f))
        val subW = subMaxW.coerceAtMost(desiredSubW)
        val subH = (subW / ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO).toInt().coerceAtMost((centerAreaHeight - dp(40f)).coerceAtLeast(60))

        val combinedW = mainW + gapBetweenScreens + subW
        val startX = safeLeft + leftRail + ((centerAreaWidth - combinedW) / 2).coerceAtLeast(0)
        val mainRect = Rect(startX, centerAreaTop + (centerAreaHeight - mainH) / 2, mainW, mainH)
        val subRect = Rect(startX + mainW + gapBetweenScreens, mainRect.y, subW, subH)
        val hasBottomRightPocket = (subRect.width >= dp(136f)) && (safeTop + safeHeight - subRect.bottom >= dp(36f))

        val controls = buildLandscapeControls(
            width = width,
            height = height,
            safeInsets = safe,
            bottomSystemCenterX = if (hasBottomRightPocket) subRect.x + subRect.width / 2 else null,
        )
        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(mainRect, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(subRect, LayoutComponent.BOTTOM_SCREEN),
            ) + controls
        )
    }

    fun buildProportionalLandscapeLayout(width: Int, height: Int, insets: Insets): ScreenLayout {
        val safe = effectiveInsets(insets)
        val safeLeft = safe.left
        val safeTop = safe.top
        val safeRight = safe.right
        val safeBottom = safe.bottom
        val safeWidth = (width - safeLeft - safeRight).coerceAtLeast(240)
        val safeHeight = (height - safeTop - safeBottom).coerceAtLeast(180)

        val leftRail = if (safeWidth >= dp(480f)) dp(114f) else 0
        val rightRail = if (safeWidth >= dp(480f)) dp(122f) else 0
        val centerAreaWidth = (safeWidth - leftRail - rightRail).coerceAtLeast(dp(240f))
        val topReserve = dp(38f)
        val bottomReserve = dp(38f)
        val centerAreaHeight = (safeHeight - topReserve - bottomReserve).coerceAtLeast((safeHeight * 0.72f).toInt())
        val centerAreaTop = safeTop + topReserve

        var sWidth = (centerAreaHeight * consoleAspectRatio).toInt()
        var sHeight = centerAreaHeight
        if (sWidth > centerAreaWidth) {
            sWidth = centerAreaWidth
            sHeight = (sWidth / consoleAspectRatio).toInt()
        }
        val x = safeLeft + leftRail + (centerAreaWidth - sWidth) / 2
        val y = centerAreaTop + (centerAreaHeight - sHeight) / 2
        val screenRect = Rect(x, y, sWidth, sHeight)

        val controls = buildLandscapeControls(width, height, safe)
        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(screenRect, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(Rect(0, 0, 0, 0), LayoutComponent.BOTTOM_SCREEN),
            ) + controls
        )
    }

    fun buildFullscreenLandscapeLayout(width: Int, height: Int, insets: Insets): ScreenLayout {
        val safe = effectiveInsets(insets)
        val screenRect = Rect(0, 0, width, height)
        val controls = buildLandscapeControls(width, height, safe)

        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(screenRect, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(Rect(0, 0, 0, 0), LayoutComponent.BOTTOM_SCREEN),
            ) + controls
        )
    }
}
