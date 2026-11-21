package com.storm3ds.emulator

import android.util.Log
import com.storm3ds.emulator.nativebridge.NativeLibrary

object NativeCoreLoader {
    @Volatile
    private var loaded = false

    @Volatile
    var librashaderAvailable = false
        private set

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val coreOk = NativeLibrary.ensureLoaded()
            if (!coreOk) {
                System.loadLibrary("storm3ds")
            }
            librashaderAvailable = runCatching {
                System.loadLibrary("librashader")
                true
            }.getOrDefault(false)
            loaded = true
            Log.i(TAG, "STORM 3DS core loaded (libstorm3ds.so, librashader=$librashaderAvailable)")
        }
    }

    private const val TAG = "Storm3dsNativeCore"
}
