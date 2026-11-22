package com.storm3ds.emulator.ui.emulator.input

import android.annotation.SuppressLint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.storm3ds.emulator.MelonEmulator.onScreenRelease
import com.storm3ds.emulator.domain.model.Input
import com.storm3ds.emulator.domain.model.Point
import com.storm3ds.emulator.domain.model.ThreeDS
import kotlin.math.roundToInt

class TouchscreenInputHandler(
    inputListener: IInputListener,
    private val viewRectProvider: (() -> RectF)? = null,
) : BaseInputHandler(inputListener) {
    private val touchPoint: Point = Point()
    private var touchActive = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val width = if (v.width > 0) v.width else 1
        val height = if (v.height > 0) v.height else 1

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                val pointerIndex = getBestPointerIndex(event)
                activePointerId = event.getPointerId(pointerIndex)

                if (!touchActive) {
                    touchActive = true
                    inputListener.onKeyPress(Input.TOUCHSCREEN)
                    val tool = event.getToolType(pointerIndex)
                    if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                        StylusHapticHelper.performStylusClick(v)
                    }
                }
                val rawX = event.getX(pointerIndex)
                val rawY = event.getY(pointerIndex)
                inputListener.onTouch(mapRawCoordsToDsPoint(rawX, rawY, width, height))
            }
            MotionEvent.ACTION_MOVE -> {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                if (touchActive) {
                    val pointerIndex = getBestPointerIndex(event)
                    activePointerId = event.getPointerId(pointerIndex)

                    // Process high-frequency historical digitizer samples (120Hz/240Hz/480Hz)
                    val historySize = event.historySize
                    for (h in 0 until historySize) {
                        val histX = event.getHistoricalX(pointerIndex, h)
                        val histY = event.getHistoricalY(pointerIndex, h)
                        inputListener.onTouch(mapRawCoordsToDsPoint(histX, histY, width, height))
                    }

                    val rawX = event.getX(pointerIndex)
                    val rawY = event.getY(pointerIndex)
                    inputListener.onTouch(mapRawCoordsToDsPoint(rawX, rawY, width, height))
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val actionIndex = event.actionIndex
                val releasedPointerId = event.getPointerId(actionIndex)
                if (activePointerId == releasedPointerId) {
                    val remainingPointerIndex = findRemainingBestPointerIndex(event, actionIndex)
                    if (remainingPointerIndex != -1) {
                        activePointerId = event.getPointerId(remainingPointerIndex)
                        val rawX = event.getX(remainingPointerIndex)
                        val rawY = event.getY(remainingPointerIndex)
                        inputListener.onTouch(mapRawCoordsToDsPoint(rawX, rawY, width, height))
                    } else {
                        endTouch()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endTouch()
            }
        }
        return true
    }

    private fun endTouch() {
        if (touchActive) {
            inputListener.onKeyReleased(Input.TOUCHSCREEN)
            onScreenRelease()
            touchActive = false
            activePointerId = MotionEvent.INVALID_POINTER_ID
        }
    }

    private fun getBestPointerIndex(event: MotionEvent): Int {
        // Priority 1: Stylus or Eraser tool (Palm Rejection)
        for (i in 0 until event.pointerCount) {
            val tool = event.getToolType(i)
            if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                return i
            }
        }
        // Priority 2: Existing active pointer if still valid
        if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
            val idx = event.findPointerIndex(activePointerId)
            if (idx >= 0 && idx < event.pointerCount) {
                return idx
            }
        }
        // Priority 3: Action index or default to pointer 0
        return event.actionIndex.coerceIn(0, event.pointerCount - 1)
    }

    private fun findRemainingBestPointerIndex(event: MotionEvent, excludeIndex: Int): Int {
        for (i in 0 until event.pointerCount) {
            if (i == excludeIndex) continue
            val tool = event.getToolType(i)
            if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                return i
            }
        }
        for (i in 0 until event.pointerCount) {
            if (i != excludeIndex) {
                return i
            }
        }
        return -1
    }

    private fun mapRawCoordsToDsPoint(rawX: Float, rawY: Float, viewWidth: Int, viewHeight: Int): Point {
        val targetW = ThreeDS.BOTTOM_SCREEN_WIDTH
        val targetH = ThreeDS.BOTTOM_SCREEN_HEIGHT
        val safeWidth = if (viewWidth > 0) viewWidth.toFloat() else targetW.toFloat()
        val safeHeight = if (viewHeight > 0) viewHeight.toFloat() else targetH.toFloat()

        val rect = viewRectProvider?.invoke()
        if (rect == null || rect.width() <= 0f || rect.height() <= 0f) {
            touchPoint.x = ((rawX / safeWidth) * targetW).roundToInt().coerceIn(0, targetW - 1)
            touchPoint.y = ((rawY / safeHeight) * targetH).roundToInt().coerceIn(0, targetH - 1)
            return touchPoint
        }

        val normalizedX = ((rawX - rect.left) / rect.width() * targetW)
        val normalizedY = ((rawY - rect.top) / rect.height() * targetH)

        touchPoint.x = normalizedX.roundToInt().coerceIn(0, targetW - 1)
        touchPoint.y = normalizedY.roundToInt().coerceIn(0, targetH - 1)
        return touchPoint
    }
}
