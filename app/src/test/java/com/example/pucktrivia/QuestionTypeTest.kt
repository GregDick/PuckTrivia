package com.example.pucktrivia

import com.example.pucktrivia.model.QuestionType
import com.example.pucktrivia.model.SeasonMode
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
    fun `GOALIES_SAVE_PCT has correct statKey`() {
        assertEquals("savePctg", QuestionType.GOALIES_SAVE_PCT.statKey)
    }

    @Test
    fun `regular season question text for DEFENDERS_POINTS`() {
        assertEquals(
            "Which of these defenders currently has the most points?",
            QuestionType.DEFENDERS_POINTS.questionText(SeasonMode.RegularSeason),
        )
    }

    @Test
    fun `regular season question text for FORWARDS_POINTS`() {
        assertEquals(
            "Which of these forwards currently has the most points?",
            QuestionType.FORWARDS_POINTS.questionText(SeasonMode.RegularSeason),
        )
    }

    @Test
    fun `regular season question text for DEFENDERS_GOALS`() {
        assertEquals(
            "Which of these defenders currently has the most goals?",
            QuestionType.DEFENDERS_GOALS.questionText(SeasonMode.RegularSeason),
        )
    }

    @Test
    fun `regular season question text for FORWARDS_GOALS`() {
        assertEquals(
            "Which of these forwards currently has the most goals?",
            QuestionType.FORWARDS_GOALS.questionText(SeasonMode.RegularSeason),
        )
    }

    @Test
    fun `regular season question text for GOALIES_SAVE_PCT`() {
        assertEquals(
            "Which of these goalies currently has the highest save percentage?",
            QuestionType.GOALIES_SAVE_PCT.questionText(SeasonMode.RegularSeason),
        )
    }

    @Test
    fun `playoffs question text for DEFENDERS_POINTS`() {
        assertEquals(
            "Which of these defenders has the most playoff points?",
            QuestionType.DEFENDERS_POINTS.questionText(SeasonMode.Playoffs),
        )
    }

    @Test
    fun `playoffs question text for FORWARDS_POINTS`() {
        assertEquals(
            "Which of these forwards has the most playoff points?",
            QuestionType.FORWARDS_POINTS.questionText(SeasonMode.Playoffs),
        )
    }

    @Test
    fun `playoffs question text for DEFENDERS_GOALS`() {
        assertEquals(
            "Which of these defenders has the most playoff goals?",
            QuestionType.DEFENDERS_GOALS.questionText(SeasonMode.Playoffs),
        )
    }

    @Test
    fun `playoffs question text for FORWARDS_GOALS`() {
        assertEquals(
            "Which of these forwards has the most playoff goals?",
            QuestionType.FORWARDS_GOALS.questionText(SeasonMode.Playoffs),
        )
    }

    @Test
    fun `playoffs question text for GOALIES_SAVE_PCT`() {
        assertEquals(
            "Which of these goalies has the highest playoff save percentage?",
            QuestionType.GOALIES_SAVE_PCT.questionText(SeasonMode.Playoffs),
        )
    }

    @Test
    fun `playoffs copy drops the word currently for all types`() {
        for (type in QuestionType.entries) {
            val playoffText = type.questionText(SeasonMode.Playoffs)
            assertEquals(
                "$type playoff copy must not contain the word 'currently'",
                false,
                playoffText.contains("currently"),
            )
            assertEquals(
                "$type playoff copy must contain the word 'playoff'",
                true,
                playoffText.contains("playoff"),
            )
        }
    }

    @Test
    fun `all skater types have poolFraction of point5`() {
        val skaterTypes = QuestionType.entries.filter { it.positionGroup != null }
        for (type in skaterTypes) {
            assertEquals("$type should have poolFraction 0.5", 0.5, type.poolFraction, 0.001)
        }
    }

    @Test
    fun `all skater types have non-null positionGroup`() {
        val skaterTypes = QuestionType.entries.filter { it.positionGroup != null }
        for (type in skaterTypes) {
            assertNotNull("$type should have non-null positionGroup", type.positionGroup)
        }
    }
}
