package com.example.pucktrivia.data

import com.example.pucktrivia.model.HighScore

/**
 * Serialises the high-score history to a single delimited string for storage, and back. A
 * hand-rolled format keeps a JSON library off the production classpath. The leading schema token
 * makes a future format change detectable.
 *
 * Format: `v1;score,endedAt;score,endedAt;...` — an empty history encodes to just `v1`.
 */
internal object HighScoreCodec {

    private const val SCHEMA_VERSION = "v1"
    private const val ENTRY_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = ","

    fun encode(history: List<HighScore>): String = buildString {
        append(SCHEMA_VERSION)
        for (entry in history) {
            append(ENTRY_SEPARATOR)
            append(entry.score)
            append(FIELD_SEPARATOR)
            append(entry.endedAt)
        }
    }

    /**
     * Decodes a payload produced by [encode]. Any malformed or unrecognised payload — including one
     * written by a future schema version — yields an empty history rather than throwing, so a
     * corrupt store can never crash the app.
     */
    fun decode(payload: String?): List<HighScore> {
        if (payload.isNullOrEmpty()) return emptyList()
        val tokens = payload.split(ENTRY_SEPARATOR)
        if (tokens.firstOrNull() != SCHEMA_VERSION) return emptyList()
        return try {
            tokens.drop(1).map { entry ->
                val fields = entry.split(FIELD_SEPARATOR)
                require(fields.size == 2) { "Expected 2 fields, got ${fields.size}" }
                HighScore(score = fields[0].toInt(), endedAt = fields[1].toLong())
            }
        } catch (e: NumberFormatException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }
}
