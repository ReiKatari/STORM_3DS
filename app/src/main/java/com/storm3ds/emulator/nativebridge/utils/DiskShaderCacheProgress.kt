package com.storm3ds.emulator.nativebridge.utils

import androidx.annotation.Keep
import com.storm3ds.emulator.R
import com.storm3ds.emulator.nativebridge.NativeBridgeHost
import com.storm3ds.emulator.nativebridge.NativeLibrary

@Keep
object DiskShaderCacheProgress {
    @JvmStatic
    fun loadProgress(stage: LoadCallbackStage, progress: Int, max: Int, obj: String) {
        val activity = NativeLibrary.sEmulationActivity.get()
        val msg = when (stage) {
            LoadCallbackStage.Prepare -> ""
            LoadCallbackStage.Decompile -> activity?.getString(R.string.preparing_shaders) ?: "Preparing shaders..."
            LoadCallbackStage.Build -> activity?.getString(R.string.building_shaders, obj) ?: "Building shaders ($obj)..."
            LoadCallbackStage.Complete -> {
                NativeBridgeHost.onEmulationStartedCallback?.invoke()
                ""
            }
        }
        NativeBridgeHost.shaderProgressCallback?.invoke(stage.name, progress, max, msg)
    }

    enum class LoadCallbackStage {
        Prepare,
        Decompile,
        Build,
        Complete
    }
}
