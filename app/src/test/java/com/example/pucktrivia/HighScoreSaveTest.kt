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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Covers Story 2: the finished game's score is saved exactly once on game over. */
@OptIn(ExperimentalCoroutinesApi::class)
class HighScoreSaveTest {

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

    private fun createStatsJson(): String {
        fun p(id: Int, name: String, pos: String, value: Double) =
            """{"id":$id,"firstName":{"default":"$name"},"lastName":{"default":"Player"},""" +
                """"sweaterNumber":${id + 10},"teamAbbrev":"TST","position":"$pos","value":$value}"""
        val players =
            listOf(
                    p(1, "Alice", "C", 100.0),
                    p(2, "Bob", "C", 80.0),
                    p(3, "Carol", "C", 60.0),
                    p(4, "Dave", "C", 40.0),
                    p(5, "Eve", "C", 20.0),
                    p(6, "Frank", "C", 10.0),
                    p(11, "Greg", "D", 90.0),
                    p(12, "Hana", "D", 70.0),
                    p(13, "Ivan", "D", 50.0),
                    p(14, "Jess", "D", 30.0),
                    p(15, "Karl", "D", 15.0),
                    p(16, "Lena", "D", 5.0),
                )
                .joinToString(",")
        return """{"points":[$players]}"""
    }

    private fun enqueueDefaultResponse() {
        mockWebServer.enqueue(MockResponse().setBody(createStatsJson()).setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
    }

    private fun fakeProvider(skaterUrl: String, goalieUrl: String): StatsUrlProvider =
        object : StatsUrlProvider {
            override fun skaterUrl(mode: SeasonMode) = skaterUrl

            override fun goalieUrl(mode: SeasonMode) = goalieUrl
        }

    private fun createViewModel(
        repository: FakeHighScoreRepository = FakeHighScoreRepository(),
        timeProvider: FixedTimeProvider = FixedTimeProvider(),
    ): TriviaViewModel {
        val skaterUrl = mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
        val goalieUrl = mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()
        return TriviaViewModel(
            OkHttpClient(),
            fakeProvider(skaterUrl, goalieUrl),
            repository,
            timeProvider,
            testDispatcher,
        )
    }

    private fun TriviaViewModel.selectWrong() {
        val wrongId = choices.first { it.id != correctPlayer!!.id }.id
        selectAnswer(wrongId)
    }

    private fun TriviaViewModel.selectCorrect() {
        selectAnswer(correctPlayer!!.id)
    }

    /** Drives a freshly started game to game over by losing all three lives. */
    private fun TriviaViewModel.loseGame() {
        repeat(3) {
            selectWrong()
            nextRound()
        }
    }

    @Test
    fun `game over submits the final score exactly once`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val repository = FakeHighScoreRepository()
            val viewModel = createViewModel(repository = repository)
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.selectCorrect()
            viewModel.nextRound()
            viewModel.loseGame()
            advanceUntilIdle()

            assertTrue(viewModel.gameOver)
            assertEquals(1, repository.submissions.size)
            assertEquals(100, repository.submissions.first().score)
        }

    @Test
    fun `recorded timestamp comes from the time provider`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val repository = FakeHighScoreRepository()
            val viewModel =
                createViewModel(
                    repository = repository,
                    timeProvider = FixedTimeProvider(now = 1_715_000_000_000L),
                )
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.loseGame()
            advanceUntilIdle()

            assertEquals(1_715_000_000_000L, repository.submissions.first().endedAt)
        }

    @Test
    fun `triggering nextRound again after game over does not resubmit`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val repository = FakeHighScoreRepository()
            val viewModel = createViewModel(repository = repository)
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.loseGame()
            viewModel.nextRound() // an extra trigger, as a stray recomposition path might cause
            advanceUntilIdle()

            assertEquals(1, repository.submissions.size)
        }

    @Test
    fun `a second game after resetGame submits an independent entry`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            enqueueDefaultResponse() // second game's fetch
            val repository = FakeHighScoreRepository()
            val viewModel = createViewModel(repository = repository)

            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()
            viewModel.selectCorrect()
            viewModel.nextRound()
            viewModel.loseGame()
            advanceUntilIdle()

            viewModel.resetGame()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()
            viewModel.loseGame()
            advanceUntilIdle()

            assertEquals(2, repository.submissions.size)
            assertEquals(100, repository.submissions[0].score)
            assertEquals(0, repository.submissions[1].score)
        }

    @Test
    fun `a storage failure still leaves the game in the game-over state`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val repository = FakeHighScoreRepository().apply { failOnSubmit = true }
            val viewModel = createViewModel(repository = repository)
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.loseGame()
            advanceUntilIdle()

            assertTrue(viewModel.gameOver)
        }

    @Test
    fun `placement and leaderboard are exposed after a placing game`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val repository = FakeHighScoreRepository()
            val viewModel = createViewModel(repository = repository)
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.selectCorrect()
            viewModel.nextRound()
            viewModel.loseGame()
            advanceUntilIdle()

            assertTrue(viewModel.placedInTopThree)
            assertEquals(listOf(100), viewModel.highScores.map { it.score })
            assertEquals(100, viewModel.currentGameHighScore?.score)
        }

    @Test
    fun `resetGame clears high-score state`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val repository = FakeHighScoreRepository()
            val viewModel = createViewModel(repository = repository)
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()
            viewModel.selectCorrect()
            viewModel.nextRound()
            viewModel.loseGame()
            advanceUntilIdle()

            viewModel.resetGame()

            assertFalse(viewModel.placedInTopThree)
            assertTrue(viewModel.highScores.isEmpty())
            assertNull(viewModel.currentGameHighScore)
        }
}
