package com.storm3ds.emulator.di.entrypoint

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.storm3ds.emulator.domain.repositories.CheatsRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CheatsEntryPoint {
    fun cheatsRepository(): CheatsRepository
    fun database(): com.storm3ds.emulator.database.MelonDatabase

    companion object {
        fun resolve(context: Context): CheatsEntryPoint {
            val applicationContext = context.applicationContext ?: throw IllegalStateException()
            return EntryPointAccessors.fromApplication(
                context = applicationContext,
                entryPoint = CheatsEntryPoint::class.java
            )
        }
    }
}
