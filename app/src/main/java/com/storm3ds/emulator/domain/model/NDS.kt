package com.storm3ds.emulator.domain.model

const val SCREEN_WIDTH = ThreeDS.TOP_SCREEN_WIDTH
const val SCREEN_HEIGHT = ThreeDS.TOP_SCREEN_HEIGHT

val consoleAspectRatio: Float
    get() = ThreeDS.TOP_SCREEN_ASPECT_RATIO

val bottomScreenAspectRatio: Float
    get() = ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO
