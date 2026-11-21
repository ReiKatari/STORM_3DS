package com.storm3ds.emulator.di.entrypoint

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.storm3ds.emulator.initializer.AppForegroundStateTrackerInitializer
import com.storm3ds.emulator.initializer.CoilInitializer

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InitializerEntryPoint {

    fun inject(coilInitializer: CoilInitializer)

    fun inject(appForegroundStateTrackerInitializer: AppForegroundStateTrackerInitializer)

    companion object {
        fun resolve(context: Context): InitializerEntryPoint {
            val applicationContext = context.applicationContext ?: throw IllegalStateException()
            return EntryPointAccessors.fromApplication(
                context = applicationContext,
                entryPoint = InitializerEntryPoint::class.java
            )
        }
    }
}