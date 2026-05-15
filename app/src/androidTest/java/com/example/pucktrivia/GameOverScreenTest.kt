package com.example.pucktrivia

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pucktrivia.model.HighScore
import com.example.pucktrivia.ui.theme.PuckTriviaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Covers Story 3: the Game Over screen's high-score display and celebration. */
class GameOverScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val threeScores =
        listOf(
            HighScore(score = 1200, endedAt = 1_715_000_000_000L),
            HighScore(score = 800, endedAt = 1_714_000_000_000L),
            HighScore(score = 500, endedAt = 1_713_000_000_000L),
        )

    @Test
    fun threeEntryListRendersAllThreeRows() {
        composeRule.setContent {
            PuckTriviaTheme {
                GameOverScreen(
                    score = 1200,
                    correctAnswered = 12,
                    totalAnswered = 15,
                    highScores = threeScores,
                    placedInTopThree = true,
                    currentGameEntry = threeScores.first(),
                    onPlayAgain = {},
                )
            }
        }

        composeRule.onNodeWithText("1.  1200").assertIsDisplayed()
        composeRule.onNodeWithText("2.  800").assertIsDisplayed()
        composeRule.onNodeWithText("3.  500").assertIsDisplayed()
    }

    @Test
    fun oneEntryListRendersASingleRow() {
        val single = listOf(HighScore(score = 400, endedAt = 1_715_000_000_000L))
        composeRule.setContent {
            PuckTriviaTheme {
                GameOverScreen(
                    score = 400,
                    correctAnswered = 4,
                    totalAnswered = 9,
                    highScores = single,
                    placedInTopThree = true,
                    currentGameEntry = single.first(),
                    onPlayAgain = {},
                )
            }
        }

        composeRule.onNodeWithText("1.  400").assertIsDisplayed()
        composeRule.onNodeWithText("2.  800").assertDoesNotExist()
    }

    @Test
    fun emptyListRendersNoHighScoreSectionAndDoesNotCrash() {
        composeRule.setContent {
            PuckTriviaTheme {
                GameOverScreen(
                    score = 0,
                    correctAnswered = 0,
                    totalAnswered = 5,
                    highScores = emptyList(),
                    placedInTopThree = false,
                    currentGameEntry = null,
                    onPlayAgain = {},
                )
            }
        }

        composeRule.onNodeWithText("Game Over").assertIsDisplayed()
        composeRule.onNodeWithText("High Scores").assertDoesNotExist()
    }

    @Test
    fun celebrationMessageShownWhenPlaced() {
        composeRule.setContent {
            PuckTriviaTheme {
                GameOverScreen(
                    score = 1200,
                    correctAnswered = 12,
                    totalAnswered = 15,
                    highScores = threeScores,
                    placedInTopThree = true,
                    currentGameEntry = threeScores.first(),
                    onPlayAgain = {},
                )
            }
        }

        composeRule.onNodeWithText("New top-3 score!").assertIsDisplayed()
    }

    @Test
    fun celebrationMessageHiddenWhenNotPlaced() {
        composeRule.setContent {
            PuckTriviaTheme {
                GameOverScreen(
                    score = 100,
                    correctAnswered = 1,
                    totalAnswered = 8,
                    highScores = threeScores,
                    placedInTopThree = false,
                    currentGameEntry = null,
                    onPlayAgain = {},
                )
            }
        }

        composeRule.onNodeWithText("New top-3 score!").assertDoesNotExist()
    }

    @Test
    fun existingGameOverElementsRemainAndPlayAgainInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            PuckTriviaTheme {
                GameOverScreen(
                    score = 700,
                    correctAnswered = 7,
                    totalAnswered = 10,
                    highScores = threeScores,
                    placedInTopThree = false,
                    currentGameEntry = null,
                    onPlayAgain = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("Game Over").assertIsDisplayed()
        composeRule.onNodeWithText("Score: 700").assertIsDisplayed()
        composeRule.onNodeWithText("7 / 10 correct").assertIsDisplayed()
        composeRule.onNodeWithText("Play Again").assertIsDisplayed()
        composeRule.onNodeWithText("Play Again").performClick()
        assertTrue(clicked)
    }

    @Test
    fun currentGameRowIsMarkedInItsAccessibilityLabel() {
        composeRule.setContent {
            PuckTriviaTheme {
                GameOverScreen(
                    score = 1200,
                    correctAnswered = 12,
                    totalAnswered = 15,
                    highScores = threeScores,
                    placedInTopThree = true,
                    currentGameEntry = threeScores.first(),
                    onPlayAgain = {},
                )
            }
        }

        // The current game's row carries a "this game" marker in its content description;
        // exactly one row should have it.
        composeRule.onNodeWithContentDescription("this game", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("Rank 1", substring = true).assertIsDisplayed()
    }
}
