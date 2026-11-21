package com.storm3ds.emulator.domain.model

enum class Input(val keyCode: Int) {
    A(0),
    B(1),
    SELECT(2),
    START(3),
    RIGHT(4),
    LEFT(5),
    UP(6),
    DOWN(7),
    R(8),
    L(9),
    X(10),
    Y(11),
    ZL(12),
    ZR(13),
    HOME(14),
    DEBUG(16 + 3),
    TOUCHSCREEN(16 + 6),
    HINGE(16 + 7),
    PAUSE(-1),
    FAST_FORWARD(-1),
    MICROPHONE(-1),
    RESET(-1),
    TOGGLE_SOFT_INPUT(-1),
    SWAP_SCREENS(-1),
    QUICK_SAVE(-1),
    QUICK_LOAD(-1),
    REWIND(-1),
    HOLD_FAST_FORWARD(-1),
    TRANSLATE(-1),
    TOGGLE_EXTRA_BUTTONS(-1),
    LOCK_ROTATION(-1),
    TOGGLE_ANALOG_STICK(-1),
    SLOW_MOTION(-1),
    FRAME_ADVANCE(-1),
    TAKE_SCREENSHOT(-1),
    TOGGLE_HARDWARE_TURBO(-1),
    COMBO_1(-1),
    COMBO_2(-1);

    val isSystemInput: Boolean
        get() = keyCode != -1

    companion object {
        val SYSTEM_BUTTONS = listOf(A, B, X, Y, L, R, ZL, ZR, START, SELECT, HOME, LEFT, RIGHT, UP, DOWN)
    }
}
