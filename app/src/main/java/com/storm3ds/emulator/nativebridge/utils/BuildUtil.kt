package com.storm3ds.emulator.nativebridge.utils

import com.storm3ds.emulator.BuildConfig

object BuildUtil {
    object BuildFlavors {
        const val GOOGLEPLAY = "googlePlay"
        const val VANILLA = "vanilla"
    }

    fun assertNotGooglePlay() {
        if (isGooglePlayBuild) {
            error("Non-GooglePlay code being called in GooglePlay build")
        }
    }

    val isGooglePlayBuild = BuildConfig.FLAVOR == BuildFlavors.GOOGLEPLAY
}
