package com.storm3ds.emulator.ui.emulator.input

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.storm3ds.emulator.common.vibration.TouchVibrator
import com.storm3ds.emulator.domain.model.Input
import com.storm3ds.emulator.ui.common.views.IAnimatedInputView

class SingleButtonInputHandler(
    inputListener: IInputListener,
    private val input: Input,
    enableHapticFeedback: Boolean,
    touchVibrator: TouchVibrator,
    private val onLongPress: (() -> Unit)? = null,
) : FeedbackInputHandler(inputListener, enableHapticFeedback, touchVibrator) {

    private var pressed = false
    private var touchDown = false
    private var isLongPressed = false
    private var activeView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (touchDown) return true
                touchDown = true
                activeView = v
                isLongPressed = false
                if (onLongPress != null) {
                    pressed = true
                    v.isPressed = true
                    (v as? IAnimatedInputView)?.updatePressedInputs(setOf(input))
                    val runnable = Runnable {
                        if (touchDown && pressed) {
                            isLongPressed = true
                            performHapticFeedback(v, HapticFeedbackType.KEY_PRESS)
                            onLongPress.invoke()
                        }
                    }
                    longPressRunnable = runnable
                    handler.postDelayed(runnable, 500L)
                } else {
                    setPressed(v, true)
                    performHapticFeedback(v, HapticFeedbackType.KEY_PRESS)
                }
            }
            MotionEvent.ACTION_UP -> {
                touchDown = false
                if (onLongPress != null) {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressRunnable = null
                    if (pressed) {
                        pressed = false
                        v.isPressed = false
                        if (!isLongPressed) {
                            inputListener.onKeyPress(input)
                            performHapticFeedback(v, HapticFeedbackType.KEY_PRESS)
                            inputListener.onKeyReleased(input)
                        }
                        (v as? IAnimatedInputView)?.updatePressedInputs(emptySet())
                    }
                    activeView = null
                } else {
                    release()
                    performHapticFeedback(v, HapticFeedbackType.KEY_RELEASE)
                }
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    fun release() {
        touchDown = false
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
        activeView?.let {
            if (onLongPress != null) {
                pressed = false
                it.isPressed = false
                (it as? IAnimatedInputView)?.updatePressedInputs(emptySet())
            } else {
                setPressed(it, false)
            }
        }
        activeView = null
    }

    private fun setPressed(view: View, value: Boolean) {
        if (pressed == value) return
        pressed = value
        view.isPressed = value
        (view as? IAnimatedInputView)?.updatePressedInputs(if (value) setOf(input) else emptySet())
        if (value) inputListener.onKeyPress(input) else inputListener.onKeyReleased(input)
    }
}
