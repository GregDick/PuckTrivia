package com.example.pucktrivia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LivesSystemTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        mockWebServer.shutdown()
    }

    private fun createStatsJson(players: List<Triple<Int, String, Double>>): String {
        val playersJson =
            players.joinToString(",") { (id, name, value) ->
                """
            {
                "id": $id,
                "firstName": {"default": "$name"},
                "lastName": {"default": "Player"},
                "sweaterNumber": ${id + 10},
                "teamAbbrev": "TST",
                "position": "C",
                "value": $value
            }
            """
                    .trimIndent()
            }
        return """{"points": [$playersJson]}"""
    }

    private fun enqueueDefaultResponse() {
        val json =
            createStatsJson(
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 60.0),
                    Triple(4, "Dave", 40.0),
                    Triple(5, "Eve", 20.0),
                )
            )
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
    }

    private fun createViewModel(): TriviaViewModel {
        val url = mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
        return TriviaViewModel(OkHttpClient(), url, testDispatcher)
    }

    private fun TriviaViewModel.selectWrong() {
        val wrongId = choices.first { it.id != correctPlayer!!.id }.id
        selectAnswer(wrongId)
    }

    private fun TriviaViewModel.selectCorrect() {
        selectAnswer(correctPlayer!!.id)
    }

    // --- Lives initial state ---

    @Test
    fun `lives starts at 3`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(3, viewModel.lives)
        }

    // --- Lives on wrong answer ---

    @Test
    fun `wrong answer decrements lives by 1`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectWrong()

            assertEquals(2, viewModel.lives)
        }

    @Test
    fun `three wrong answers reduces lives to 0`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()

            assertEquals(0, viewModel.lives)
        }

    @Test
    fun `lives never go below 0`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Lose all 3 lives
            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()
            // gameOver is now true; but verify lives is 0 not negative
            assertEquals(0, viewModel.lives)
        }

    // --- Lives on correct answer ---

    @Test
    fun `correct answer does not change lives`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectCorrect()

            assertEquals(3, viewModel.lives)
        }

    @Test
    fun `multiple correct answers leave lives at 3`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            repeat(3) {
                viewModel.selectCorrect()
                viewModel.nextRound()
            }

            assertEquals(3, viewModel.lives)
        }

    // --- Score unaffected by wrong answers ---

    @Test
    fun `score is unchanged after wrong answer`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectCorrect()
            assertEquals(100, viewModel.score)

            viewModel.nextRound()
            viewModel.selectWrong()

            assertEquals(100, viewModel.score)
        }

    // --- Counter tracking ---

    @Test
    fun `totalAnswered increments on every answer`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(0, viewModel.totalAnswered)

            viewModel.selectCorrect()
            assertEquals(1, viewModel.totalAnswered)

            viewModel.nextRound()
            viewModel.selectWrong()
            assertEquals(2, viewModel.totalAnswered)
        }

    @Test
    fun `correctAnswered increments only on correct answers`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(0, viewModel.correctAnswered)

            viewModel.selectCorrect()
            assertEquals(1, viewModel.correctAnswered)

            viewModel.nextRound()
            viewModel.selectWrong()
            assertEquals(1, viewModel.correctAnswered)
        }

    @Test
    fun `answer 5 questions 3 correct 2 wrong tracks counters accurately`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectCorrect()
            viewModel.nextRound()
            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectCorrect()
            viewModel.nextRound()
            viewModel.selectCorrect()
            viewModel.nextRound()
            viewModel.selectWrong()

            assertEquals(5, viewModel.totalAnswered)
            assertEquals(3, viewModel.correctAnswered)
        }

    // --- Game over transition ---

    @Test
    fun `gameOver is false initially`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.gameOver)
        }

    @Test
    fun `nextRound with lives at 0 sets gameOver true`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()

            assertFalse(viewModel.gameOver) // Still on feedback screen
            viewModel.nextRound()
            assertTrue(viewModel.gameOver)
        }

    @Test
    fun `choices unchanged after gameOver transition`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()

            val choicesBeforeGameOver = viewModel.choices
            viewModel.nextRound()

            assertEquals(choicesBeforeGameOver, viewModel.choices)
        }

    // --- resetGame ---

    @Test
    fun `resetGame resets all state to initial values`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Play a game to completion
            viewModel.selectCorrect()
            viewModel.nextRound()
            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()
            viewModel.nextRound() // gameOver = true

            assertTrue(viewModel.gameOver)

            viewModel.resetGame()

            assertEquals(3, viewModel.lives)
            assertEquals(0, viewModel.score)
            assertEquals(0, viewModel.totalAnswered)
            assertEquals(0, viewModel.correctAnswered)
            assertFalse(viewModel.gameOver)
            assertFalse(viewModel.answered)
            assertEquals(3, viewModel.choices.size)
        }

    @Test
    fun `resetGame resets used player pools so choices come from a fresh pool`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectCorrect()
            viewModel.nextRound()
            viewModel.selectCorrect()
            viewModel.nextRound()

            viewModel.resetGame()

            // After reset, pointsUsedIds contains exactly the 3 IDs from the new round
            // (the pool was cleared and prepareRound ran fresh).
            assertEquals(viewModel.choices.map { it.id }.toSet(), viewModel.pointsUsedIds)
            assertEquals(3, viewModel.pointsUsedIds.size)
        }
}
