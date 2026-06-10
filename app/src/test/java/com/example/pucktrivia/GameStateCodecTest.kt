package com.example.pucktrivia

import com.example.pucktrivia.data.GameSnapshot
import com.example.pucktrivia.data.GameStateCodec
import com.example.pucktrivia.model.GoalieStatLeader
import com.example.pucktrivia.model.QuestionType
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.model.SkaterStatLeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateCodecTest {

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
        choices: List<com.example.pucktrivia.model.StatLeader> = listOf(skaterA, skaterB),
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

    @Test
    fun `encode then decode round-trips regular-season snapshot without answer`() {
        val snapshot = baseSnapshot()
        val decoded = GameStateCodec.decode(GameStateCodec.encode(snapshot))
        assertNotNull(decoded)
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `encode then decode round-trips snapshot with selected answer`() {
        val snapshot = baseSnapshot(selectedPlayerId = skaterA.id)
        val decoded = GameStateCodec.decode(GameStateCodec.encode(snapshot))
        assertNotNull(decoded)
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `encode then decode round-trips game-over snapshot`() {
        val snapshot = baseSnapshot(gameOver = true, selectedPlayerId = skaterB.id)
        val decoded = GameStateCodec.decode(GameStateCodec.encode(snapshot))
        assertNotNull(decoded)
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `encode then decode round-trips playoffs mode`() {
        val snapshot = baseSnapshot(mode = SeasonMode.Playoffs)
        val decoded = GameStateCodec.decode(GameStateCodec.encode(snapshot))
        assertNotNull(decoded)
        assertEquals(SeasonMode.Playoffs, decoded!!.selectedMode)
    }

    @Test
    fun `encode then decode round-trips GoalieStatLeader in choices`() {
        val snapshot =
            baseSnapshot(
                    choices = listOf(skaterA, skaterB, goalie),
                    usedIds = mapOf(QuestionType.GOALIES_SAVE_PCT to setOf(99)),
                )
                .copy(correctPlayerId = goalie.id)
        val decoded = GameStateCodec.decode(GameStateCodec.encode(snapshot))
        assertNotNull(decoded)
        val restoredGoalie = decoded!!.choices.find { it.id == 99 }
        assertNotNull(restoredGoalie)
        assertTrue(restoredGoalie is GoalieStatLeader)
        assertEquals(0.923, restoredGoalie!!.value, 0.0001)
    }

    @Test
    fun `encode then decode round-trips skater with null sweater number`() {
        val snapshot = baseSnapshot(choices = listOf(skaterA, skaterB))
        val decoded = GameStateCodec.decode(GameStateCodec.encode(snapshot))
        assertNotNull(decoded)
        val bobRestored = decoded!!.choices.find { it.id == 2 }
        assertNotNull(bobRestored)
        assertNull((bobRestored as SkaterStatLeader).sweaterNumber)
    }

    @Test
    fun `encode then decode preserves usedIds map with multiple question types`() {
        val usedIds =
            mapOf(
                QuestionType.FORWARDS_POINTS to setOf(1, 2, 3),
                QuestionType.DEFENDERS_GOALS to setOf(11, 12),
                QuestionType.GOALIES_SAVE_PCT to emptySet(),
            )
        val snapshot = baseSnapshot(usedIds = usedIds)
        val decoded = GameStateCodec.decode(GameStateCodec.encode(snapshot))
        assertNotNull(decoded)
        assertEquals(usedIds, decoded!!.usedIds)
    }

    @Test
    fun `decode of null returns null`() {
        assertNull(GameStateCodec.decode(null))
    }

    @Test
    fun `decode of empty string returns null`() {
        assertNull(GameStateCodec.decode(""))
    }

    @Test
    fun `decode of garbled payload returns null`() {
        assertNull(GameStateCodec.decode("not-a-real-payload"))
        assertNull(GameStateCodec.decode("v1§abc§def"))
    }

    @Test
    fun `decode of unknown schema version returns null`() {
        // Encode a valid snapshot then swap the version token.
        val valid = GameStateCodec.encode(baseSnapshot())
        val future = valid.replaceFirst("v1§", "v99§")
        assertNull(GameStateCodec.decode(future))
    }

    @Test
    fun `decode of truncated payload returns null`() {
        // Fewer than 14 fields should be rejected.
        assertNull(GameStateCodec.decode("v1§RS§200§2§3"))
    }

    @Test
    fun `empty usedIds map round-trips`() {
        val snapshot = baseSnapshot(usedIds = emptyMap())
        val decoded = GameStateCodec.decode(GameStateCodec.encode(snapshot))
        assertNotNull(decoded)
        assertTrue(decoded!!.usedIds.isEmpty())
    }
}
