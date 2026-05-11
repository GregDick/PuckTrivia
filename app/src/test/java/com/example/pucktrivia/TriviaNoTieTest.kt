package com.example.pucktrivia

import com.example.pucktrivia.di.StatsUrlProvider
import com.example.pucktrivia.model.SeasonMode
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
 * Tests that verify no two player options ever share the same stat value.
 *
 * All players in these tests are forwards ("C") so they land in the FORWARDS_POINTS pool, making
 * the test data straightforward to reason about.
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

    /**
     * Creates JSON with only a "points" key. All players are forwards ("C") so the FORWARDS_POINTS
     * pool is the only available pool, making question type selection deterministic regardless of
     * the random seed.
     */
    private fun createStatsJson(players: List<Triple<Int, String, Double>>): String {
        val playersJson =
            players.joinToString(",") { (id, name, value) ->
                """{"id":$id,"firstName":{"default":"$name"},"lastName":{"default":"Player"},"sweaterNumber":${id + 10},"teamAbbrev":"TST","position":"C","value":$value}"""
            }
        return """{"points":[$playersJson]}"""
    }

    private fun fakeProvider(skaterUrl: String, goalieUrl: String): StatsUrlProvider =
        object : StatsUrlProvider {
            override fun skaterUrl(mode: SeasonMode) = skaterUrl

            override fun goalieUrl(mode: SeasonMode) = goalieUrl
        }

    private fun createViewModel(): TriviaViewModel {
        val skaterUrl = mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
        val goalieUrl = mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()
        return TriviaViewModel(OkHttpClient(), fakeProvider(skaterUrl, goalieUrl), testDispatcher)
    }

    @Test
    fun `all choices must have distinct point values when pool contains multi-way tie`() =
        runTest(testDispatcher) {
            // 8 players → pool of 4. Two-way tie at 50 inside the pool; 70 and 60 provide
            // the 3 distinct values needed for the pool to be viable.
            val json =
                createStatsJson(
                    listOf(
                        Triple(1, "Alice", 70.0),
                        Triple(2, "Bob", 60.0),
                        Triple(3, "Carol", 50.0),
                        Triple(4, "Dave", 50.0),
                        Triple(5, "Eve", 40.0),
                        Triple(6, "Frank", 30.0),
                        Triple(7, "Grace", 20.0),
                        Triple(8, "Hank", 10.0),
                    )
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val values = viewModel.choices.map { it.value }
            assertEquals(3, values.size)
            assertEquals(
                "All choice point values must be unique, but got: $values",
                values.size,
                values.distinct().size,
            )
        }

    @Test
    fun `correct answer must not tie with any other choice`() =
        runTest(testDispatcher) {
            // 8 players → pool of 4. Pair of 100s at top; 80 and 50 ensure ≥3 distinct values.
            val json =
                createStatsJson(
                    listOf(
                        Triple(1, "Alice", 100.0),
                        Triple(2, "Bob", 100.0),
                        Triple(3, "Carol", 80.0),
                        Triple(4, "Dave", 50.0),
                        Triple(5, "Eve", 40.0),
                        Triple(6, "Frank", 30.0),
                        Triple(7, "Grace", 20.0),
                        Triple(8, "Hank", 10.0),
                    )
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
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
            // 8 players → pool of 4. Pair of 50s as potential non-correct choices.
            val json =
                createStatsJson(
                    listOf(
                        Triple(1, "Alice", 100.0),
                        Triple(2, "Bob", 80.0),
                        Triple(3, "Carol", 50.0),
                        Triple(4, "Dave", 50.0),
                        Triple(5, "Eve", 40.0),
                        Triple(6, "Frank", 30.0),
                        Triple(7, "Grace", 20.0),
                        Triple(8, "Hank", 10.0),
                    )
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
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
            // 8 players → pool of 4. Pair of 80s inside the pool; 100 and 60 provide 3 distinct
            // values.
            val json =
                createStatsJson(
                    listOf(
                        Triple(1, "Alice", 100.0),
                        Triple(2, "Bob", 80.0),
                        Triple(3, "Carol", 80.0),
                        Triple(4, "Dave", 60.0),
                        Triple(5, "Eve", 40.0),
                        Triple(6, "Frank", 30.0),
                        Triple(7, "Grace", 20.0),
                        Triple(8, "Hank", 10.0),
                    )
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val values = viewModel.choices.map { it.value }
            assertEquals(3, values.size)
            assertEquals(
                "All choice point values must be unique, but got: $values",
                values.size,
                values.distinct().size,
            )
        }
}
