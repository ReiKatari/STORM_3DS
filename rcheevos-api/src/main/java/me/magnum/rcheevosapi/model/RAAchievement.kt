package me.magnum.rcheevosapi.model

import java.net.URL

data class RAAchievement(
    val id: Long,
    val gameId: RAGameId,
    val setId: RASetId,
    val totalAwardsCasual: Int?,
    val totalAwardsHardcore: Int?,
    val title: String,
    val description: String,
    val points: Int,
    val displayOrder: Int,
    val badgeUrlUnlocked: URL,
    val badgeUrlLocked: URL,
    val memoryAddress: String,
    val type: Type,
    val categoryTag: String? = null,
) {

    enum class Type {
        CORE,
        UNOFFICIAL
    }

    fun getCleanTitle(): String {
        return title
            .removeSuffix("[m]")
            .removeSuffix("[p]")
            .removeSuffix("[w]")
            .trim()
    }

    fun isMissable(): Boolean {
        return title.endsWith("[m]") || categoryTag.equals("missable", ignoreCase = true)
    }

    fun isProgression(): Boolean {
        return categoryTag.equals("progression", ignoreCase = true) || title.endsWith("[p]")
    }

    fun isWinCondition(): Boolean {
        return categoryTag.equals("win_condition", ignoreCase = true) ||
            categoryTag.equals("win", ignoreCase = true) ||
            title.endsWith("[w]")
    }
}