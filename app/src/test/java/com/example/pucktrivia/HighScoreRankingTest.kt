package com.example.pucktrivia

import com.example.pucktrivia.data.HighScoreRanking
import com.example.pucktrivia.model.HighScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighScoreRankingTest {

    private fun score(value: Int, endedAt: Long = value.toLong()) =
        HighScore(score = value, endedAt = endedAt)

    @Test
    fun `topThree of empty history is empty`() {
        assertTrue(HighScoreRanking.topThree(emptyList()).isEmpty())
    }

    @Test
    fun `topThree returns existing entries when fewer than three`() {
        val history = listOf(score(100), score(300))
        assertEquals(listOf(300, 100), HighScoreRanking.topThree(history).map { it.score })
    }

    @Test
    fun `topThree returns the three highest sorted descending`() {
        val history = listOf(score(100), score(500), score(200), score(400), score(300))
        assertEquals(listOf(500, 400, 300), HighScoreRanking.topThree(history).map { it.score })
    }

    @Test
    fun `topThree excludes zero scores`() {
        val history = listOf(score(0, 1), score(0, 2), score(100, 3))
        assertEquals(listOf(score(100, 3)), HighScoreRanking.topThree(history))
    }

    @Test
    fun `topThree of all-zero history is empty`() {
        val history = listOf(score(0, 1), score(0, 2), score(0, 3))
        assertTrue(HighScoreRanking.topThree(history).isEmpty())
    }

    @Test
    fun `topThree breaks score ties most-recent-first`() {
        val older = HighScore(score = 100, endedAt = 1_000L)
        val newer = HighScore(score = 100, endedAt = 2_000L)
        assertEquals(listOf(newer, older), HighScoreRanking.topThree(listOf(older, newer)))
    }

    @Test
    fun `placesInTopThree is true for any positive score into empty history`() {
        assertTrue(HighScoreRanking.placesInTopThree(emptyList(), score(50)))
    }

    @Test
    fun `placesInTopThree is true when fewer than three eligible entries exist`() {
        val history = listOf(score(900), score(800))
        assertTrue(HighScoreRanking.placesInTopThree(history, score(1)))
    }

    @Test
    fun `placesInTopThree is true when strictly greater than third place`() {
        val history = listOf(score(300), score(200), score(100))
        assertTrue(HighScoreRanking.placesInTopThree(history, score(150)))
    }

    @Test
    fun `placesInTopThree is false when equal to third place`() {
        val history = listOf(score(300), score(200), score(100))
        assertFalse(HighScoreRanking.placesInTopThree(history, score(100, endedAt = 999L)))
    }

    @Test
    fun `placesInTopThree is false when lower than third place`() {
        val history = listOf(score(300), score(200), score(100))
        assertFalse(HighScoreRanking.placesInTopThree(history, score(50)))
    }

    @Test
    fun `placesInTopThree is false for a zero score even with open slots`() {
        assertFalse(HighScoreRanking.placesInTopThree(emptyList(), score(0)))
    }

    @Test
    fun `a history of only zero scores leaves all slots open for a positive score`() {
        val history = listOf(score(0, 1), score(0, 2), score(0, 3))
        assertTrue(HighScoreRanking.placesInTopThree(history, score(10)))
    }
}
