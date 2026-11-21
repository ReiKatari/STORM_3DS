package com.storm3ds.emulator.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.storm3ds.emulator.database.entities.PlaySessionEntity
import com.storm3ds.emulator.database.entities.PlayStatsEntity

@Dao
interface PlayStatsDao {

    @Query("SELECT * FROM play_stats")
    suspend fun getAllPlayStats(): List<PlayStatsEntity>

    @Query("SELECT * FROM play_stats")
    fun observeAllPlayStats(): Flow<List<PlayStatsEntity>>

    @Query("SELECT * FROM play_stats WHERE romUri = :romUri")
    suspend fun getPlayStatsForRom(romUri: String): PlayStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayStats(stats: PlayStatsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStats(stats: List<PlayStatsEntity>)

    @Insert
    suspend fun insertSession(session: PlaySessionEntity)

    @Insert
    suspend fun insertAllSessions(sessions: List<PlaySessionEntity>)

    @Query("SELECT * FROM play_sessions ORDER BY startedAtMs DESC")
    suspend fun getAllSessions(): List<PlaySessionEntity>

    @Query("SELECT * FROM play_sessions ORDER BY startedAtMs DESC")
    fun observeAllSessions(): Flow<List<PlaySessionEntity>>

    @Query("SELECT * FROM play_sessions WHERE dayKey = :dayKey ORDER BY startedAtMs DESC")
    suspend fun getSessionsForDay(dayKey: String): List<PlaySessionEntity>

    @Query("SELECT DISTINCT dayKey FROM play_sessions ORDER BY startedAtMs DESC")
    suspend fun getDistinctDays(): List<String>
}
