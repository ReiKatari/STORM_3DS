package com.storm3ds.emulator.domain.model.retroachievements

data class PendingAchievementsSyncSummary(
    val totalCount: Int,
    val succeededCount: Int,
    val failedCount: Int,
    val discardedHardcoreCount: Int = 0,
    val lastErrorMessage: String? = null,
)
