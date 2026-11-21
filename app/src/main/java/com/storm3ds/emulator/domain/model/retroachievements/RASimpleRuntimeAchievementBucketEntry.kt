package com.storm3ds.emulator.domain.model.retroachievements

data class RASimpleRuntimeAchievementBucketEntry(
    val achievementId: Long,
    val subsetId: Long,
    val bucketType: Int,
)
