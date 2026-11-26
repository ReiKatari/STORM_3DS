package com.storm3ds.emulator.ui.romdetails.model

sealed class RomRetroAchievementsUiState {
    data class LoggedOut(val existingUsername: String?) : RomRetroAchievementsUiState()
    object Loading : RomRetroAchievementsUiState()
    data class Ready(
        val sets: List<AchievementSetUiModel>,
        val pendingLedgerAchievementIds: Set<Long> = emptySet(),
        val gameTitle: String = "",
        val gameCode: String = "",
        val romHash: String = "",
        val raGameId: Long? = null,
    ) : RomRetroAchievementsUiState() {

        fun hasAchievements(): Boolean {
            return sets.any { it.buckets.isNotEmpty() }
        }
    }
    object LoginError : RomRetroAchievementsUiState()
    object AchievementLoadError : RomRetroAchievementsUiState()
}
