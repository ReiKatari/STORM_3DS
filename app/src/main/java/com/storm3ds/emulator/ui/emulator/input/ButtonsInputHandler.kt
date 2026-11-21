package com.storm3ds.emulator.ui.emulator.input

import com.storm3ds.emulator.common.vibration.TouchVibrator
import com.storm3ds.emulator.domain.model.Input

class ButtonsInputHandler(inputListener: IInputListener, enableHapticFeedback: Boolean, touchVibrator: TouchVibrator) : MultiButtonInputHandler(inputListener, enableHapticFeedback, touchVibrator) {
    override fun getTopInput() = Input.X
    override fun getLeftInput() = Input.Y
    override fun getBottomInput() = Input.B
    override fun getRightInput() = Input.A
}