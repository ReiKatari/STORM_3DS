package com.storm3ds.emulator.debug

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.storm3ds.emulator.domain.repositories.RomsRepository
import com.storm3ds.emulator.domain.repositories.SaveStatesRepository
import com.storm3ds.emulator.domain.repositories.SettingsRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DebugCommandEntryPoint {
    fun sharedPreferences(): SharedPreferences
    fun settingsRepository(): SettingsRepository
    fun romsRepository(): RomsRepository
    fun saveStatesRepository(): SaveStatesRepository

    companion object {
        fun resolve(context: Context): DebugCommandEntryPoint {
            val applicationContext = context.applicationContext ?: throw IllegalStateException()
            return EntryPointAccessors.fromApplication(
                applicationContext,
                DebugCommandEntryPoint::class.java,
            )
        }
    }
}
