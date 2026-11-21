package com.storm3ds.emulator.domain.repositories

import kotlinx.coroutines.flow.Flow
import com.storm3ds.emulator.domain.model.Background
import java.util.UUID

interface BackgroundRepository {
    fun getBackgrounds(): Flow<List<Background>>
    suspend fun getBackground(id: UUID): Background?
    suspend fun addBackground(background: Background)
    suspend fun deleteBackground(background: Background)
}