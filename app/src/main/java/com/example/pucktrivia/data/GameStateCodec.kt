package com.example.pucktrivia.data

import com.example.pucktrivia.model.GoalieStatLeader
import com.example.pucktrivia.model.QuestionType
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.model.SkaterStatLeader
import com.example.pucktrivia.model.StatLeader

/**
 * Serialises the minimal in-progress game snapshot to a single string for storage in
 * [androidx.lifecycle.SavedStateHandle], and back. A hand-rolled format keeps a JSON library off
 * the production classpath — following the [HighScoreCodec] precedent. The leading schema token
 * makes a future format change detectable; any parse error or unknown version returns `null` (no
 * active game) rather than throwing, so a corrupt handle entry cannot crash the app.
 *
 * Format: `v1§<field>§<field>§...` using `§` (U+00A7) as the top-level field separator. Fields, in
 * order:
 * 1. seasonMode: "RS" | "PO"
 * 2. score: Int
 * 3. lives: Int
 * 4. roundNumber: Int
 * 5. totalAnswered: Int
 * 6. correctAnswered: Int
 * 7. gameOver: "1" | "0"
 * 8. selectedPlayerId: Int or "" if null
 * 9. questionText
 *     10. statUnitLabel
 *     11. correctPlayerId: Int
 *     12. choices: pipe-separated StatLeader encodings (see [encodeStatLeader])
 *     13. usedIds: QuestionType.name~id,id,... entries joined by pipe
 *
 * [encodeStatLeader] format: `<type>|<id>|<firstName>|<lastName>|<sweaterNumber or
 * "">|<teamAbbrev>|<position or "">|<value>` where type is "S" (SkaterStatLeader) or "G"
 * (GoalieStatLeader).
 */
internal object GameStateCodec {

    private const val SCHEMA_VERSION = "v1"

    // § (section sign) — safe separator; will not appear in NHL player names or stat labels.
    private const val FIELD_SEP = "§"

    // | separates list entries (choices list, usedIds list)
    private const val LIST_SEP = "|"

    // ~ separates the QuestionType name from the id-list within a usedIds entry
    private const val MAP_KEY_SEP = "~"

    // , separates individual ids within a usedIds entry
    private const val ID_SEP = ","

    // Sub-field separator within a single StatLeader encoding
    private const val PLAYER_FIELD_SEP = "^"

    fun encode(snapshot: GameSnapshot): String = buildString {
        append(SCHEMA_VERSION)
        append(FIELD_SEP)
        append(if (snapshot.selectedMode == SeasonMode.RegularSeason) "RS" else "PO")
        append(FIELD_SEP)
        append(snapshot.score)
        append(FIELD_SEP)
        append(snapshot.lives)
        append(FIELD_SEP)
        append(snapshot.roundNumber)
        append(FIELD_SEP)
        append(snapshot.totalAnswered)
        append(FIELD_SEP)
        append(snapshot.correctAnswered)
        append(FIELD_SEP)
        append(if (snapshot.gameOver) "1" else "0")
        append(FIELD_SEP)
        append(snapshot.selectedPlayerId?.toString() ?: "")
        append(FIELD_SEP)
        append(snapshot.questionText)
        append(FIELD_SEP)
        append(snapshot.statUnitLabel)
        append(FIELD_SEP)
        append(snapshot.correctPlayerId)
        append(FIELD_SEP)
        append(snapshot.choices.joinToString(LIST_SEP) { encodeStatLeader(it) })
        append(FIELD_SEP)
        append(
            snapshot.usedIds.entries.joinToString(LIST_SEP) { (type, ids) ->
                "${type.name}$MAP_KEY_SEP${ids.joinToString(ID_SEP)}"
            }
        )
    }

    /**
     * Decodes a payload produced by [encode]. Returns `null` for any malformed, empty, or
     * unrecognised payload so the caller treats it as "no active game".
     */
    fun decode(payload: String?): GameSnapshot? {
        if (payload.isNullOrEmpty()) return null
        return try {
            val fields = payload.split(FIELD_SEP)
            if (fields.size < 14) return null
            if (fields[0] != SCHEMA_VERSION) return null

            val selectedMode =
                when (fields[1]) {
                    "RS" -> SeasonMode.RegularSeason
                    "PO" -> SeasonMode.Playoffs
                    else -> return null
                }
            val score = fields[2].toInt()
            val lives = fields[3].toInt()
            val roundNumber = fields[4].toInt()
            val totalAnswered = fields[5].toInt()
            val correctAnswered = fields[6].toInt()
            val gameOver = fields[7] == "1"
            val selectedPlayerId = fields[8].takeIf { it.isNotEmpty() }?.toInt()
            val questionText = fields[9]
            val statUnitLabel = fields[10]
            val correctPlayerId = fields[11].toInt()

            val choicesRaw = fields[12]
            val choices: List<StatLeader> =
                if (choicesRaw.isEmpty()) emptyList()
                else choicesRaw.split(LIST_SEP).map { decodeStatLeader(it) ?: return null }

            val usedIdsRaw = fields[13]
            val usedIds: Map<QuestionType, Set<Int>> =
                if (usedIdsRaw.isEmpty()) emptyMap()
                else {
                    val result = mutableMapOf<QuestionType, Set<Int>>()
                    for (entry in usedIdsRaw.split(LIST_SEP)) {
                        val parts = entry.split(MAP_KEY_SEP, limit = 2)
                        if (parts.size != 2) return null
                        val type = QuestionType.valueOf(parts[0]) // throws if unknown name
                        val ids =
                            if (parts[1].isEmpty()) emptySet()
                            else parts[1].split(ID_SEP).map { it.toInt() }.toSet()
                        result[type] = ids
                    }
                    result
                }

            GameSnapshot(
                selectedMode = selectedMode,
                score = score,
                lives = lives,
                roundNumber = roundNumber,
                totalAnswered = totalAnswered,
                correctAnswered = correctAnswered,
                gameOver = gameOver,
                selectedPlayerId = selectedPlayerId,
                questionText = questionText,
                statUnitLabel = statUnitLabel,
                correctPlayerId = correctPlayerId,
                choices = choices,
                usedIds = usedIds,
            )
        } catch (_: NumberFormatException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun encodeStatLeader(leader: StatLeader): String = buildString {
        val type = if (leader is SkaterStatLeader) "S" else "G"
        val position = if (leader is SkaterStatLeader) leader.position else ""
        append(type)
        append(PLAYER_FIELD_SEP)
        append(leader.id)
        append(PLAYER_FIELD_SEP)
        append(leader.firstName)
        append(PLAYER_FIELD_SEP)
        append(leader.lastName)
        append(PLAYER_FIELD_SEP)
        append(leader.sweaterNumber?.toString() ?: "")
        append(PLAYER_FIELD_SEP)
        append(leader.teamAbbrev)
        append(PLAYER_FIELD_SEP)
        append(position)
        append(PLAYER_FIELD_SEP)
        append(leader.value)
    }

    private fun decodeStatLeader(encoded: String): StatLeader? {
        val f = encoded.split(PLAYER_FIELD_SEP)
        if (f.size != 8) return null
        val type = f[0]
        val id = f[1].toInt()
        val firstName = f[2]
        val lastName = f[3]
        val sweaterNumber = f[4].takeIf { it.isNotEmpty() }?.toInt()
        val teamAbbrev = f[5]
        val position = f[6]
        val value = f[7].toDouble()
        return when (type) {
            "S" ->
                SkaterStatLeader(
                    id = id,
                    firstName = firstName,
                    lastName = lastName,
                    sweaterNumber = sweaterNumber,
                    teamAbbrev = teamAbbrev,
                    position = position,
                    value = value,
                )
            "G" ->
                GoalieStatLeader(
                    id = id,
                    firstName = firstName,
                    lastName = lastName,
                    sweaterNumber = sweaterNumber,
                    teamAbbrev = teamAbbrev,
                    value = value,
                )
            else -> null
        }
    }
}

/**
 * Minimal in-progress game snapshot. Contains only what is needed to restore the current question
 * and counters; the full fetched datasets ([TriviaViewModel.statsData], [TriviaViewModel.pools])
 * are re-fetched on the next round rather than persisted, to stay within the Bundle size limit.
 */
data class GameSnapshot(
    val selectedMode: SeasonMode,
    val score: Int,
    val lives: Int,
    val roundNumber: Int,
    val totalAnswered: Int,
    val correctAnswered: Int,
    val gameOver: Boolean,
    val selectedPlayerId: Int?,
    val questionText: String,
    val statUnitLabel: String,
    /** The id of the correct answer player, always among [choices]. */
    val correctPlayerId: Int,
    val choices: List<StatLeader>,
    val usedIds: Map<QuestionType, Set<Int>>,
)
