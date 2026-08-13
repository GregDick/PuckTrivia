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
import org.junit.Before
import org.junit.Test

/**
 * Covers the routing decision extracted from `MainActivity` for the XR work.
 *
 * The spatial layout is chosen by `isSpatialUiEnabled() && route == Question`. The capability half
 * is a CompositionLocal that needs a headset, but *which route we are on* is plain logic — so this
 * is where layout selection is actually verifiable without a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TriviaRouteTest {

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

    @Test
    fun `no selected mode routes to Start`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(TriviaRoute.Start, triviaRouteFor(viewModel))
        }

    @Test
    fun `a prepared round routes to Question`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(TriviaRoute.Question, triviaRouteFor(viewModel))
        }

    @Test
    fun `a failed fetch routes to LoadError, never Question`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            // Guards the branch ordering that keeps MainActivity from dereferencing
            // correctPlayer!! with no question prepared.
            assertEquals(TriviaRoute.LoadError, triviaRouteFor(viewModel))
        }

    @Test
    fun `game over outranks Question even with choices present`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            // Burn all three lives; every wrong answer costs one.
            repeat(3) {
                val wrong = viewModel.choices.first { it.id != viewModel.correctPlayer?.id }
                viewModel.selectAnswer(wrong.id)
                viewModel.nextRound()
                advanceUntilIdle()
            }

            assertEquals(TriviaRoute.GameOver, triviaRouteFor(viewModel))
        }

    private fun fakeProvider(skaterUrl: String, goalieUrl: String): StatsUrlProvider =
        object : StatsUrlProvider {
            override fun skaterUrl(mode: SeasonMode) = skaterUrl

            override fun goalieUrl(mode: SeasonMode) = goalieUrl
        }

    private fun createViewModel(): TriviaViewModel {
        val skaterUrl = mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
        val goalieUrl = mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()
        return TriviaViewModel(
            OkHttpClient(),
            fakeProvider(skaterUrl, goalieUrl),
            FakeHighScoreRepository(),
            FixedTimeProvider(),
            testDispatcher,
        )
    }

    private fun enqueueDefaultResponse() {
        fun player(id: Int, value: Double, position: String) =
            """{"id":$id,"firstName":{"default":"P$id"},"lastName":{"default":"Player"},"sweaterNumber":${id + 10},"teamAbbrev":"TST","position":"$position","value":$value}"""

        val forwards = (1..6).map { player(it, 110.0 - it * 10, "C") }
        val defenders = (11..16).map { player(it, 100.0 - it, "D") }
        val skaterJson =
            """{"points":[${(forwards + defenders).joinToString(",")}],"goals":[${(forwards + defenders).joinToString(",")}]}"""

        mockWebServer.enqueue(MockResponse().setBody(skaterJson))
        mockWebServer.enqueue(MockResponse().setBody("""{"savePctg":[]}"""))
    }
}
