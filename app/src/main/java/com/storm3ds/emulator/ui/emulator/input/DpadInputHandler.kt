package com.storm3ds.emulator.ui.emulator.input

import com.storm3ds.emulator.common.vibration.TouchVibrator
import com.storm3ds.emulator.domain.model.Input

class DpadInputHandler(
    inputListener: IInputListener,
    enableHapticFeedback: Boolean,
    touchVibrator: TouchVibrator
) : MultiButtonInputHandler(inputListener, enableHapticFeedback, touchVibrator) {

    override val isRadialDpad: Boolean = true

    override fun getTopInput() = Input.UP
    override fun getLeftInput() = Input.LEFT
    override fun getBottomInput() = Input.DOWN
    override fun getRightInput() = Input.RIGHT
}