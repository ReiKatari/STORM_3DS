package com.storm3ds.emulator.initializer

import android.content.Context
import androidx.startup.Initializer
import com.storm3ds.emulator.di.entrypoint.InitializerEntryPoint
import com.storm3ds.emulator.impl.system.AppForegroundStateTracker
import javax.inject.Inject

class AppForegroundStateTrackerInitializer : Initializer<Unit> {

    @Inject
    lateinit var appForegroundStateTracker: AppForegroundStateTracker

    override fun create(context: Context) {
        InitializerEntryPoint.resolve(context).inject(this)
        appForegroundStateTracker.startTracking(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> = emptyList()
}