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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TriviaViewModelTest {

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

    @Test
    fun `initial score is 0`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(0, viewModel.score)
        }

    @Test
    fun `selecting correct answer increments score by 100`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            val correctId = viewModel.correctPlayer!!.id
            viewModel.selectAnswer(correctId)

            assertEquals(100, viewModel.score)
        }

    @Test
    fun `selecting wrong answer does not change score`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            // First get a correct answer to have a non-zero score
            val correctId = viewModel.correctPlayer!!.id
            viewModel.selectAnswer(correctId)
            assertEquals(100, viewModel.score)

            // Next round
            viewModel.nextRound()

            // Select a wrong answer — score should remain 100
            val wrongId = viewModel.choices.first { it.id != viewModel.correctPlayer!!.id }.id
            viewModel.selectAnswer(wrongId)

            assertEquals(100, viewModel.score)
        }

    @Test
    fun `consecutive correct answers accumulate`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Round 1: correct
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            assertEquals(100, viewModel.score)

            // Round 2: correct
            viewModel.nextRound()
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            assertEquals(200, viewModel.score)

            // Round 3: correct
            viewModel.nextRound()
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            assertEquals(300, viewModel.score)
        }

    @Test
    fun `after wrong answer next correct answer accumulates score`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Correct answer
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            assertEquals(100, viewModel.score)

            // Wrong answer — score stays at 100
            viewModel.nextRound()
            val wrongId = viewModel.choices.first { it.id != viewModel.correctPlayer!!.id }.id
            viewModel.selectAnswer(wrongId)
            assertEquals(100, viewModel.score)

            // Correct answer again — score becomes 200
            viewModel.nextRound()
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            assertEquals(200, viewModel.score)
        }

    @Test
    fun `nextRound produces 3 new choices`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(3, viewModel.choices.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            assertEquals(3, viewModel.choices.size)
        }

    @Test
    fun `choices remain stable across multiple reads`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            val firstRead = viewModel.choices
            val secondRead = viewModel.choices
            val thirdRead = viewModel.choices

            assertEquals(firstRead, secondRead)
            assertEquals(secondRead, thirdRead)
        }

    @Test
    fun `answered is false initially and true after selection`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.answered)
            viewModel.selectAnswer(viewModel.choices.first().id)
            assertTrue(viewModel.answered)
        }

    @Test
    fun `nextRound clears selection`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectAnswer(viewModel.choices.first().id)
            assertTrue(viewModel.answered)

            viewModel.nextRound()
            assertFalse(viewModel.answered)
            assertNull(viewModel.selectedPlayerId)
        }

    @Test
    fun `correctPlayer is the player with highest value among choices`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            advanceUntilIdle()

            val correct = viewModel.correctPlayer
            assertNotNull(correct)
            val maxValue = viewModel.choices.maxOf { it.value }
            assertEquals(maxValue, correct!!.value, 0.001)
        }
}
