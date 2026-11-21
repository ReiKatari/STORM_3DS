package com.storm3ds.emulator.ui.emulator.component

import java.net.URL
import com.storm3ds.emulator.domain.model.retroachievements.RAEvent
import com.storm3ds.emulator.ui.emulator.model.RAEventUi

internal object LeaderboardScoreboardUiMapper {
    fun map(
        key: LeaderboardAttemptKey,
        scoreboard: RAEvent.OnLeaderboardScoreboard,
        title: String,
        gameIcon: URL?,
    ): RAEventUi.LeaderboardEntrySubmitted {
        return RAEventUi.LeaderboardEntrySubmitted(
            leaderboardId = key.leaderboardId,
            attemptKey = key,
            title = title,
            gameIcon = gameIcon,
            submittedScore = scoreboard.submittedScore,
            bestScore = scoreboard.bestScore.takeIf(String::isNotEmpty),
            rank = scoreboard.newRank,
            numberOfEntries = scoreboard.numEntries,
        )
    }
}
