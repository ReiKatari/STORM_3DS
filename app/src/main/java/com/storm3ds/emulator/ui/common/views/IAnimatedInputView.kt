package com.storm3ds.emulator.ui.common.views

import com.storm3ds.emulator.domain.model.Input

interface IAnimatedInputView {
    fun updatePressedInputs(pressedInputs: Set<Input>)
}
