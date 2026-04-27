package com.example.pucktrivia

import com.example.pucktrivia.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuestionTypeTest {

    @Test
    fun `GOALIES_SAVE_PCT has null positionGroup`() {
        assertNull(QuestionType.GOALIES_SAVE_PCT.positionGroup)
    }

    @Test
    fun `GOALIES_SAVE_PCT has poolFraction of 1`() {
        assertEquals(1.0, QuestionType.GOALIES_SAVE_PCT.poolFraction, 0.001)
    }

    @Test
    fun `GOALIES_SAVE_PCT has minWins of 10`() {
        assertEquals(10, QuestionType.GOALIES_SAVE_PCT.minWins)
    }

    @Test
    fun `GOALIES_SAVE_PCT has correct statKey`() {
        assertEquals("savePctg", QuestionType.GOALIES_SAVE_PCT.statKey)
    }

    @Test
    fun `GOALIES_SAVE_PCT has correct question text`() {
        assertEquals(
            "Which of these goalies currently has the highest save percentage?",
            QuestionType.GOALIES_SAVE_PCT.questionText,
        )
    }

    @Test
    fun `all skater types have poolFraction of 0point5`() {
        val skaterTypes =
            listOf(
                QuestionType.DEFENDERS_POINTS,
                QuestionType.FORWARDS_POINTS,
                QuestionType.DEFENDERS_GOALS,
                QuestionType.FORWARDS_GOALS,
            )
        for (type in skaterTypes) {
            assertEquals("$type should have poolFraction 0.5", 0.5, type.poolFraction, 0.001)
        }
    }

    @Test
    fun `all skater types have minWins of 0`() {
        val skaterTypes =
            listOf(
                QuestionType.DEFENDERS_POINTS,
                QuestionType.FORWARDS_POINTS,
                QuestionType.DEFENDERS_GOALS,
                QuestionType.FORWARDS_GOALS,
            )
        for (type in skaterTypes) {
            assertEquals("$type should have minWins 0", 0, type.minWins)
        }
    }

    @Test
    fun `all skater types have non-null positionGroup`() {
        val skaterTypes =
            listOf(
                QuestionType.DEFENDERS_POINTS,
                QuestionType.FORWARDS_POINTS,
                QuestionType.DEFENDERS_GOALS,
                QuestionType.FORWARDS_GOALS,
            )
        for (type in skaterTypes) {
            assertNotNull("$type should have non-null positionGroup", type.positionGroup)
        }
    }
}
