package com.storm3ds.emulator.impl.emulator

import java.nio.ByteBuffer

class EmulatorMessageQueue(private val eventHandler: EventHandler) {

    fun interface EventHandler {
        fun onEmulatorEvent(type: EmulatorEventType, data: ByteBuffer)
    }

    @Volatile
    private var isRunning = false

    fun start() {
        isRunning = true
    }

    fun stop() {
        isRunning = false
    }

    fun cleanup() {
        isRunning = false
    }

    fun isActive(): Boolean = isRunning
}
