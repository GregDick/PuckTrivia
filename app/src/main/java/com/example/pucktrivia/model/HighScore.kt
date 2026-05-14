package com.example.pucktrivia.model

/**
 * A single completed game's result: the final [score] and the wall-clock time the game ended,
 * stored as epoch milliseconds. Formatting the timestamp for display is a presentation concern and
 * is intentionally not done here.
 */
data class HighScore(val score: Int, val endedAt: Long)
