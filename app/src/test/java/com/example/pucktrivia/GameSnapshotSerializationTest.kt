package com.example.pucktrivia

import com.example.pucktrivia.data.GameSnapshot
import com.example.pucktrivia.model.GoalieStatLeader
import com.example.pucktrivia.model.QuestionType
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.model.SkaterStatLeader
import com.example.pucktrivia.model.StatLeader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that [GameSnapshot] survives a Java-serialization round-trip — the mechanism the
 * framework uses to write it into the saved-state Bundle for process-death survival. Runs on the
 * plain JVM (no emulator), unlike a `Parcel` round-trip would.
 */
class GameSnapshotSerializationTest {

    private val skaterA =
        SkaterStatLeader(
            id = 1,
            firstName = "Alice",
            lastName = "Player",
            sweaterNumber = 11,
            teamAbbrev = "TST",
            position = "C",
            value = 100.0,
        )
    private val skaterB =
        SkaterStatLeader(
            id = 2,
            firstName = "Bob",
            lastName = "Smith",
            sweaterNumber = null,
            teamAbbrev = "OTH",
            position = "D",
            value = 80.0,
        )
    private val goalie =
        GoalieStatLeader(
            id = 99,
            firstName = "Guy",
            lastName = "Goalie",
            sweaterNumber = 31,
            teamAbbrev = "TST",
            value = 0.923,
        )

    private fun baseSnapshot(
        mode: SeasonMode = SeasonMode.RegularSeason,
        gameOver: Boolean = false,
        selectedPlayerId: Int? = null,
        choices: List<StatLeader> = listOf(skaterA, skaterB),
        usedIds: Map<QuestionType, Set<Int>> = mapOf(QuestionType.FORWARDS_POINTS to setOf(1, 2)),
    ) =
        GameSnapshot(
            selectedMode = mode,
            score = 200,
            lives = 2,
            roundNumber = 3,
            totalAnswered = 3,
            correctAnswered = 2,
            gameOver = gameOver,
            selectedPlayerId = selectedPlayerId,
            questionText = "Who has the most points?",
            statUnitLabel = "pts",
            correctPlayerId = skaterA.id,
            choices = choices,
            usedIds = usedIds,
        )

    private fun GameSnapshot.serializationRoundTrip(): GameSnapshot {
        val bytes =
            ByteArrayOutputStream().use { buffer ->
                ObjectOutputStream(buffer).use { it.writeObject(this) }
                buffer.toByteArray()
            }
        return ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as GameSnapshot
        }
    }

    @Test
    fun `regular-season snapshot without answer round-trips`() {
        val snapshot = baseSnapshot()
        assertEquals(snapshot, snapshot.serializationRoundTrip())
    }

    @Test
    fun `snapshot with selected answer round-trips`() {
        val snapshot = baseSnapshot(selectedPlayerId = skaterA.id)
        assertEquals(snapshot, snapshot.serializationRoundTrip())
    }

    @Test
    fun `game-over snapshot round-trips`() {
        val snapshot = baseSnapshot(gameOver = true, selectedPlayerId = skaterB.id)
        assertEquals(snapshot, snapshot.serializationRoundTrip())
    }

    @Test
    fun `playoffs mode round-trips`() {
        val snapshot = baseSnapshot(mode = SeasonMode.Playoffs)
        assertEquals(SeasonMode.Playoffs, snapshot.serializationRoundTrip().selectedMode)
    }

    @Test
    fun `goalie in choices round-trips as goalie type`() {
        val snapshot =
            baseSnapshot(
                    choices = listOf(skaterA, skaterB, goalie),
                    usedIds = mapOf(QuestionType.GOALIES_SAVE_PCT to setOf(99)),
                )
                .copy(correctPlayerId = goalie.id)
        val restored = snapshot.serializationRoundTrip()
        assertEquals(snapshot, restored)
        val restoredGoalie = restored.choices.find { it.id == 99 }
        assertTrue("goalie type preserved", restoredGoalie is GoalieStatLeader)
        assertEquals(0.923, restoredGoalie!!.value, 0.0001)
    }

    @Test
    fun `skater with null sweater number round-trips`() {
        val restored = baseSnapshot(choices = listOf(skaterA, skaterB)).serializationRoundTrip()
        val bob = restored.choices.find { it.id == 2 } as SkaterStatLeader
        assertNull(bob.sweaterNumber)
    }

    @Test
    fun `usedIds map with multiple question types round-trips`() {
        val usedIds =
            mapOf(
                QuestionType.FORWARDS_POINTS to setOf(1, 2, 3),
                QuestionType.DEFENDERS_GOALS to setOf(11, 12),
                QuestionType.GOALIES_SAVE_PCT to emptySet(),
            )
        val restored = baseSnapshot(usedIds = usedIds).serializationRoundTrip()
        assertEquals(usedIds, restored.usedIds)
    }

    @Test
    fun `empty usedIds map round-trips`() {
        val restored = baseSnapshot(usedIds = emptyMap()).serializationRoundTrip()
        assertTrue(restored.usedIds.isEmpty())
    }
}
