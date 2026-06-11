package com.example.pucktrivia.data

import com.example.pucktrivia.model.QuestionType
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.model.StatLeader
import java.io.Serializable

/**
 * Minimal in-progress game snapshot persisted in [androidx.lifecycle.SavedStateHandle] so an active
 * game survives OS-initiated process death. It is [Serializable] and converted to a `ByteArray` by
 * [GameSnapshotSerializer] for storage in the handle — no hand-rolled field-by-field codec.
 * (`@Parcelize` would be the idiomatic choice, but the parcelize compiler plugin does not activate
 * under AGP 9's built-in Kotlin — see https://issuetracker.google.com/issues/389977429.)
 *
 * Only what is needed to restore the current question and counters is stored; the full fetched
 * datasets ([TriviaViewModel.statsData], [TriviaViewModel.pools]) are re-fetched on the next round
 * rather than persisted, keeping the payload small and well clear of the Bundle/binder size limit.
 *
 * No explicit `serialVersionUID` is declared, deliberately: the JVM-computed default changes
 * whenever the class shape changes, making deserialisation of a stale snapshot throw.
 * [GameSnapshotSerializer.fromBytes] catches that and treats it as "no active game" — the same
 * fail-safe schema-versioning contract [HighScoreCodec] implements by hand.
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
) : Serializable
