package com.storm3ds.emulator.ui.emulator.input

import com.storm3ds.emulator.domain.model.Input
import com.storm3ds.emulator.domain.model.Point

interface IInputListener {
    fun onKeyPress(key: Input)
    fun onKeyReleased(key: Input)
    fun onTouch(point: Point)
}