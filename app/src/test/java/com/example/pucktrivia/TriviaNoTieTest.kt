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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that verify no two player options ever share the same point value.
 *
 * Bug: When multiple players in the data have identical point values, prepareRound() can select
 * them as choices, creating ties — including ties for the correct answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TriviaNoTieTest {

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

    private fun createViewModel(): TriviaViewModel {
        val url = mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
        return TriviaViewModel(OkHttpClient(), url, testDispatcher)
    }

    @Test
    fun `all choices must have distinct point values when all players have same points`() =
        runTest(testDispatcher) {
            // All 3 players have the same value — any selection will produce ties
            val json =
                createStatsJson(
                    listOf(
                        Triple(1, "Alice", 50.0),
                        Triple(2, "Bob", 50.0),
                        Triple(3, "Carol", 50.0),
                    )
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel()
            advanceUntilIdle()

            val values = viewModel.choices.map { it.value }
            assertEquals(
                "All choice point values must be unique, but got: $values",
                values.size,
                values.distinct().size,
            )
        }

    @Test
    fun `correct answer must not tie with any other choice`() =
        runTest(testDispatcher) {
            // Two players tied at the top value — correct answer is ambiguous
            val json =
                createStatsJson(
                    listOf(
                        Triple(1, "Alice", 100.0),
                        Triple(2, "Bob", 100.0),
                        Triple(3, "Carol", 50.0),
                    )
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel()
            advanceUntilIdle()

            val correctValue = viewModel.correctPlayer!!.value
            val otherValues =
                viewModel.choices.filter { it.id != viewModel.correctPlayer!!.id }.map { it.value }

            assertTrue(
                "Correct answer value ($correctValue) must not appear among other choices: $otherValues",
                otherValues.none { it == correctValue },
            )
        }

    @Test
    fun `non-correct choices must not tie with each other`() =
        runTest(testDispatcher) {
            // Top player is unique, but the other two are tied
            val json =
                createStatsJson(
                    listOf(
                        Triple(1, "Alice", 100.0),
                        Triple(2, "Bob", 50.0),
                        Triple(3, "Carol", 50.0),
                    )
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel()
            advanceUntilIdle()

            val nonCorrectValues =
                viewModel.choices.filter { it.id != viewModel.correctPlayer!!.id }.map { it.value }

            assertEquals(
                "Non-correct choice values must be unique, but got: $nonCorrectValues",
                nonCorrectValues.size,
                nonCorrectValues.distinct().size,
            )
        }

    @Test
    fun `choices have distinct values when pool has many duplicates`() =
        runTest(testDispatcher) {
            // Exactly 3 players with duplicate values — take(3) must select all, guaranteeing ties
            val json =
                createStatsJson(
                    listOf(
                        Triple(1, "Alice", 80.0),
                        Triple(2, "Bob", 80.0),
                        Triple(3, "Carol", 60.0),
                    )
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel()
            advanceUntilIdle()

            val values = viewModel.choices.map { it.value }
            assertEquals(
                "All choice point values must be unique, but got: $values",
                values.size,
                values.distinct().size,
            )
        }
}
