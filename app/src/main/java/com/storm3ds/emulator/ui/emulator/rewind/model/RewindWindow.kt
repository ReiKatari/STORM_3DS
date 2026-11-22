package com.storm3ds.emulator.ui.emulator.rewind.model

import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RewindWindow(
    val currentEmulationFrame: Int,
    val rewindStates: ArrayList<RewindSaveState>,
    val useMilliseconds: Boolean = false,
) {

    companion object {
        private const val FRAMES_PER_SECOND = 60
    }

    fun getDeltaFromEmulationTimeToRewindState(state: RewindSaveState): Duration {
        val elapsed = (currentEmulationFrame - state.frame).coerceAtLeast(0)
        if (useMilliseconds) {
            return elapsed.toLong().milliseconds
        }
        val elapsedMillis = elapsed.toFloat() / FRAMES_PER_SECOND * 1000
        return elapsedMillis.toLong().milliseconds
    }
}