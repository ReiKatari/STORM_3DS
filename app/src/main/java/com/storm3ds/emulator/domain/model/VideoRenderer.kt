package com.storm3ds.emulator.domain.model

enum class VideoRenderer(val renderer: Int) {
    SOFTWARE(0),
    OPENGL(1),
    VULKAN(2),
    COMPUTE(3),
}
