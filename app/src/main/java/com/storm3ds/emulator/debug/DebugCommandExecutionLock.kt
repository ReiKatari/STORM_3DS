package com.storm3ds.emulator.debug

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object DebugCommandExecutionLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T {
        return mutex.withLock {
            block()
        }
    }
}
