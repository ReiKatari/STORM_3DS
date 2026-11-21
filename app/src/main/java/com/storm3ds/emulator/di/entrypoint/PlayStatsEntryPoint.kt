package com.storm3ds.emulator.di.entrypoint

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.storm3ds.emulator.database.MelonDatabase

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayStatsEntryPoint {
    fun database(): MelonDatabase

    companion object {
        fun resolve(context: Context): PlayStatsEntryPoint {
            val applicationContext = context.applicationContext ?: throw IllegalStateException()
            return EntryPointAccessors.fromApplication(
                context = applicationContext,
                entryPoint = PlayStatsEntryPoint::class.java,
            )
        }
    }
}
