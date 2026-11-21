package com.storm3ds.emulator.ui.emulator.render

fun interface FrameRenderCallback {
    fun renderFrame(isValidFrame: Boolean, frameTextureId: Int)
}
