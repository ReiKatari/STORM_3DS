package com.storm3ds.emulator.common.vibration

interface VibratorDelegate {
    fun supportsVibration(): Boolean
    fun supportsVibrationAmplitude(): Boolean
    fun vibrate(duration: Int, amplitude: Int)
    fun startVibrating()
    fun stopVibrating()
}