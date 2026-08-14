package com.example.pucktrivia

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.model.SkaterStatLeader
import com.example.pucktrivia.ui.theme.PuckTriviaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Coverage for the Question screen, which had none before the XR work — and which that work
 * modified, since `AnswerButton` became internal so the spatial layout could reuse it.
 *
 * These assertions cover the same `AnswerButton` the spatial layout renders, so the shared answer
 * behavior is pinned in one place rather than duplicated per layout.
 */
class TriviaQuestionScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val alice = leader(id = 1, name = "Alice", value = 30.0)
    private val bob = leader(id = 2, name = "Bob", value = 20.0)
    private val carol = leader(id = 3, name = "Carol", value = 10.0)
    private val choices = listOf(alice, bob, carol)

    @Test
    fun rendersQuestionAndEveryChoice() {
        setContent(answered = false)

        composeTestRule.onNodeWithText(QUESTION).assertExists()
        choices.forEach {
            composeTestRule.onNodeWithText(it.label(), substring = true).assertExists()
        }
    }

    @Test
    fun tappingAnAnswerReportsThatPlayersId() {
        var selected: Int? = null
        setContent(answered = false, onAnswerSelected = { selected = it })

        assertNull(selected)
        composeTestRule.onNodeWithText(bob.label(), substring = true).performClick()

        assertEquals(bob.id, selected)
    }

    @Test
    fun choicesAreEnabledBeforeAnswering() {
        setContent(answered = false)

        composeTestRule.onNodeWithText(alice.label(), substring = true).assertIsEnabled()
    }

    @Test
    fun choicesAreDisabledAfterAnswering() {
        setContent(answered = true, selectedPlayerId = bob.id)

        choices.forEach {
            composeTestRule.onNodeWithText(it.label(), substring = true).assertIsNotEnabled()
        }
    }

    @Test
    fun statValuesAreHiddenUntilAnswered() {
        setContent(answered = false)

        composeTestRule.onNodeWithText("30 pts", substring = true).assertDoesNotExist()
    }

    @Test
    fun correctAnswerRevealsStatValuesAndPositiveFeedback() {
        setContent(answered = true, selectedPlayerId = alice.id)

        composeTestRule.onNodeWithText("30 pts", substring = true).assertExists()
        composeTestRule.onNodeWithText("Correct!").assertExists()
    }

    @Test
    fun wrongAnswerShowsIncorrectFeedback() {
        setContent(answered = true, selectedPlayerId = carol.id)

        composeTestRule.onNodeWithText("Incorrect!").assertExists()
    }

    private fun setContent(
        answered: Boolean,
        selectedPlayerId: Int? = null,
        onAnswerSelected: (Int) -> Unit = {},
    ) {
        composeTestRule.setContent {
            PuckTriviaTheme {
                TriviaQuestionScreen(
                    score = 100,
                    lives = 3,
                    livesColor = Color.White,
                    seasonMode = SeasonMode.RegularSeason,
                    questionText = QUESTION,
                    statUnitLabel = "pts",
                    choices = choices,
                    selectedPlayerId = selectedPlayerId,
                    correctPlayerId = alice.id,
                    answered = answered,
                    isCorrect = selectedPlayerId == alice.id,
                    onAnswerSelected = onAnswerSelected,
                    onNextRound = {},
                )
            }
        }
    }

    private fun leader(id: Int, name: String, value: Double) =
        SkaterStatLeader(
            id = id,
            firstName = name,
            lastName = "Player",
            sweaterNumber = id,
            teamAbbrev = "TST",
            position = "C",
            value = value,
        )

    private fun SkaterStatLeader.label() = "$firstName $lastName"

    private companion object {
        const val QUESTION = "Who leads the league?"
    }
}
