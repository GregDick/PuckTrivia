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

    /**
     * Creates a JSON response with 6 forwards (position "C") and 6 defenders (position "D") under
     * the "points" key. Each position group yields a top-50% pool of 3, sufficient for one round.
     */
    private fun createDefaultStatsJson(): String {
        val forwards =
            listOf(
                Triple(1, "Alice", 100.0),
                Triple(2, "Bob", 80.0),
                Triple(3, "Carol", 60.0),
                Triple(4, "Dave", 40.0),
                Triple(5, "Eve", 20.0),
                Triple(6, "Frank", 10.0),
            )
        val defenders =
            listOf(
                Triple(11, "Greg", 90.0),
                Triple(12, "Hana", 70.0),
                Triple(13, "Ivan", 50.0),
                Triple(14, "Jess", 30.0),
                Triple(15, "Karl", 15.0),
                Triple(16, "Lena", 5.0),
            )
        fun toJson(id: Int, name: String, value: Double, position: String) =
            """{"id":$id,"firstName":{"default":"$name"},"lastName":{"default":"Player"},"sweaterNumber":${id + 10},"teamAbbrev":"TST","position":"$position","value":$value}"""
        val playersJson =
            (forwards.map { (id, name, v) -> toJson(id, name, v, "C") } +
                    defenders.map { (id, name, v) -> toJson(id, name, v, "D") })
                .joinToString(",")
        return """{"points":[$playersJson]}"""
    }

    private fun enqueueDefaultResponse() {
        mockWebServer.enqueue(MockResponse().setBody(createDefaultStatsJson()).setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
    }

    private fun fakeProvider(skaterUrl: String, goalieUrl: String): StatsUrlProvider =
        object : StatsUrlProvider() {
            override fun skaterUrl(mode: SeasonMode) = skaterUrl

            override fun goalieUrl(mode: SeasonMode) = goalieUrl
        }

    private fun createViewModel(): TriviaViewModel {
        val skaterUrl = mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
        val goalieUrl = mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()
        return TriviaViewModel(OkHttpClient(), fakeProvider(skaterUrl, goalieUrl), testDispatcher)
    }

    @Test
    fun `ViewModel does not fetch on construction`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertNull(viewModel.selectedMode)
            assertFalse(viewModel.isLoading)
            assertTrue(viewModel.choices.isEmpty())
            assertEquals(0, mockWebServer.requestCount)
        }

    @Test
    fun `startGame with RegularSeason fetches regular-season URLs`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val recordedModes = mutableListOf<SeasonMode>()
            val skaterUrl =
                mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
            val goalieUrl =
                mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()
            val recordingProvider =
                object : StatsUrlProvider() {
                    override fun skaterUrl(mode: SeasonMode): String {
                        recordedModes.add(mode)
                        return skaterUrl
                    }

                    override fun goalieUrl(mode: SeasonMode): String = goalieUrl
                }
            val viewModel = TriviaViewModel(OkHttpClient(), recordingProvider, testDispatcher)
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(listOf(SeasonMode.RegularSeason), recordedModes)
            assertEquals(SeasonMode.RegularSeason, viewModel.selectedMode)
        }

    @Test
    fun `startGame with Playoffs fetches playoff URLs`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val recordedModes = mutableListOf<SeasonMode>()
            val skaterUrl =
                mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
            val goalieUrl =
                mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()
            val recordingProvider =
                object : StatsUrlProvider() {
                    override fun skaterUrl(mode: SeasonMode): String {
                        recordedModes.add(mode)
                        return skaterUrl
                    }

                    override fun goalieUrl(mode: SeasonMode): String = goalieUrl
                }
            val viewModel = TriviaViewModel(OkHttpClient(), recordingProvider, testDispatcher)
            viewModel.startGame(SeasonMode.Playoffs)
            advanceUntilIdle()

            assertEquals(listOf(SeasonMode.Playoffs), recordedModes)
            assertEquals(SeasonMode.Playoffs, viewModel.selectedMode)
        }

    @Test
    fun `resetGame returns to selectedMode null with empty pools`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertTrue(viewModel.choices.isNotEmpty())

            viewModel.resetGame()

            assertNull(viewModel.selectedMode)
            assertFalse(viewModel.isLoading)
            assertTrue(viewModel.pools.isEmpty())
            assertTrue(viewModel.choices.isEmpty())
        }

    @Test
    fun `initial score is 0`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(0, viewModel.score)
        }

    @Test
    fun `selecting correct answer increments score by 100`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val correctId = viewModel.correctPlayer!!.id
            viewModel.selectAnswer(correctId)
            assertEquals(100, viewModel.score)

            viewModel.nextRound()

            val wrongId = viewModel.choices.first { it.id != viewModel.correctPlayer!!.id }.id
            viewModel.selectAnswer(wrongId)

            assertEquals(100, viewModel.score)
        }

    @Test
    fun `consecutive correct answers accumulate`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            assertEquals(100, viewModel.score)

            viewModel.nextRound()
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            assertEquals(200, viewModel.score)

            viewModel.nextRound()
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            assertEquals(300, viewModel.score)
        }

    @Test
    fun `after wrong answer next correct answer accumulates score`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            assertEquals(100, viewModel.score)

            viewModel.nextRound()
            val wrongId = viewModel.choices.first { it.id != viewModel.correctPlayer!!.id }.id
            viewModel.selectAnswer(wrongId)
            assertEquals(100, viewModel.score)

            viewModel.nextRound()
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            assertEquals(200, viewModel.score)
        }

    @Test
    fun `nextRound produces 3 new choices`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val correct = viewModel.correctPlayer
            assertNotNull(correct)
            val maxValue = viewModel.choices.maxOf { it.value }
            assertEquals(maxValue, correct!!.value, 0.001)
        }

    @Test
    fun `empty playoff response sets playoffsUnavailable and not fatalError`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.Playoffs)
            advanceUntilIdle()

            assertTrue(viewModel.playoffsUnavailable)
            assertFalse(viewModel.fatalError)
            assertFalse(viewModel.loadError)
            assertTrue(viewModel.pools.isEmpty())
            assertTrue(viewModel.choices.isEmpty())
        }

    @Test
    fun `empty regular-season response sets fatalError not playoffsUnavailable`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertTrue(viewModel.fatalError)
            assertFalse(viewModel.playoffsUnavailable)
            assertFalse(viewModel.loadError)
        }

    @Test
    fun `network failure in playoffs sets loadError not playoffsUnavailable`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.Playoffs)
            advanceUntilIdle()

            assertTrue(viewModel.loadError)
            assertFalse(viewModel.playoffsUnavailable)
            assertFalse(viewModel.fatalError)
        }

    @Test
    fun `switching from playoffsUnavailable to RegularSeason clears flag and re-fetches`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            mockWebServer.enqueue(
                MockResponse().setBody(createDefaultStatsJson()).setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))

            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.Playoffs)
            advanceUntilIdle()
            assertTrue(viewModel.playoffsUnavailable)

            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertFalse(viewModel.playoffsUnavailable)
            assertFalse(viewModel.fatalError)
            assertEquals(SeasonMode.RegularSeason, viewModel.selectedMode)
            assertTrue(viewModel.choices.isNotEmpty())
        }

    @Test
    fun `playoffs question text uses playoff copy after fetch`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.Playoffs)
            advanceUntilIdle()

            assertTrue(
                "Expected playoff question text to contain 'playoff' but was '${viewModel.questionText}'",
                viewModel.questionText.contains("playoff"),
            )
            assertFalse(
                "Expected playoff question text to drop 'currently' but was '${viewModel.questionText}'",
                viewModel.questionText.contains("currently"),
            )
        }

    @Test
    fun `regular season question text uses regular-season copy after fetch`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertTrue(
                "Expected regular-season question text to contain 'currently' but was '${viewModel.questionText}'",
                viewModel.questionText.contains("currently"),
            )
            assertFalse(
                "Expected regular-season question text not to contain 'playoff' but was '${viewModel.questionText}'",
                viewModel.questionText.contains("playoff"),
            )
        }

    @Test
    fun `resetGame clears playoffsUnavailable flag`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.Playoffs)
            advanceUntilIdle()
            assertTrue(viewModel.playoffsUnavailable)

            viewModel.resetGame()

            assertFalse(viewModel.playoffsUnavailable)
            assertNull(viewModel.selectedMode)
        }
}
