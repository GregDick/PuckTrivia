package com.example.pucktrivia.data

import com.example.pucktrivia.model.HighScore

/** Number of entries shown on the high-score leaderboard. */
const val TOP_SCORE_COUNT = 3

/**
 * Pure ranking logic over the full persisted score history. Has no Android or storage dependencies
 * so it can be unit-tested directly.
 */
object HighScoreRanking {

    /**
     * The leaderboard view: the highest [TOP_SCORE_COUNT] scores from [history], highest first.
     * Scores of zero (or below) are never eligible and are excluded. Ties are broken
     * most-recent-first so the ordering is deterministic.
     */
    fun topThree(history: List<HighScore>): List<HighScore> =
        history
            .filter { it.score > 0 }
            .sortedWith(compareByDescending<HighScore> { it.score }.thenByDescending { it.endedAt })
            .take(TOP_SCORE_COUNT)

    /**
     * Whether [candidate] places on the leaderboard, evaluated against the history as it stood
     * *before* the candidate was recorded. A zero/negative score never places. With fewer than
     * [TOP_SCORE_COUNT] eligible entries, any positive score places. Otherwise the candidate must
     * be strictly greater than the current lowest leaderboard score — a tie does not displace.
     */
    fun placesInTopThree(priorHistory: List<HighScore>, candidate: HighScore): Boolean {
        if (candidate.score <= 0) return false
        val leaderboard = topThree(priorHistory)
        if (leaderboard.size < TOP_SCORE_COUNT) return true
        return candidate.score > leaderboard.last().score
    }
}
