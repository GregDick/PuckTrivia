package com.example.pucktrivia

import com.example.pucktrivia.data.HighScoreRanking
import com.example.pucktrivia.data.HighScoreRepository
import com.example.pucktrivia.data.LeaderboardResult
import com.example.pucktrivia.data.TimeProvider
import com.example.pucktrivia.model.HighScore

/**
 * In-memory [HighScoreRepository] for tests. Records every submission and applies the same ranking
 * logic as production so callers see realistic results. Set [failOnSubmit] to simulate a storage
 * failure.
 */
class FakeHighScoreRepository(initialHistory: List<HighScore> = emptyList()) : HighScoreRepository {

    val history = initialHistory.toMutableList()
    val submissions = mutableListOf<HighScore>()
    var failOnSubmit = false

    override suspend fun topThree(): List<HighScore> = HighScoreRanking.topThree(history)

    override suspend fun submit(score: Int, endedAt: Long): LeaderboardResult {
        if (failOnSubmit) throw RuntimeException("simulated storage failure")
        val entry = HighScore(score = score, endedAt = endedAt)
        val placed = HighScoreRanking.placesInTopThree(history, entry)
        submissions.add(entry)
        history.add(entry)
        return LeaderboardResult(
            placedInTopThree = placed,
            topThree = HighScoreRanking.topThree(history),
        )
    }
}

/** [TimeProvider] returning a fixed, mutable timestamp so timestamp tests are deterministic. */
class FixedTimeProvider(var now: Long = 1_000L) : TimeProvider {
    override fun nowMillis(): Long = now
}
