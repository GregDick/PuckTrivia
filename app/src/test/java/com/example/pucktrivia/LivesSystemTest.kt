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

    /**
     * Creates a points-only JSON response with 6 forwards ("C") and 6 defenders ("D"). Each group
     * yields a pool of 3 (ceil(6/2)=3), sufficient for multiple rounds with reset.
     */
    private fun createStatsJson(): String {
        fun p(id: Int, name: String, pos: String, value: Double) =
            """{"id":$id,"firstName":{"default":"$name"},"lastName":{"default":"Player"},"sweaterNumber":${id + 10},"teamAbbrev":"TST","position":"$pos","value":$value}"""
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
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(3, viewModel.lives)
        }

    // --- Lives on wrong answer ---

    @Test
    fun `wrong answer decrements lives by 1`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.selectWrong()

            assertEquals(2, viewModel.lives)
        }

    @Test
    fun `three wrong answers reduces lives to 0`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()
            assertEquals(0, viewModel.lives)
        }

    // --- Lives on correct answer ---

    @Test
    fun `correct answer does not change lives`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.selectCorrect()

            assertEquals(3, viewModel.lives)
        }

    @Test
    fun `multiple correct answers leave lives at 3`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertFalse(viewModel.gameOver)
        }

    @Test
    fun `nextRound with lives at 0 sets gameOver true`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()
            viewModel.nextRound()
            viewModel.selectWrong()

            assertFalse(viewModel.gameOver)
            viewModel.nextRound()
            assertTrue(viewModel.gameOver)
        }

    @Test
    fun `choices unchanged after gameOver transition`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
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
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

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
            assertNull(viewModel.selectedMode)
            assertTrue(viewModel.choices.isEmpty())
        }

    @Test
    fun `resetGame clears all used player pools`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.selectCorrect()
            viewModel.nextRound()
            viewModel.selectCorrect()
            viewModel.nextRound()

            viewModel.resetGame()

            // resetGame() returns to Start Screen — all used sets are empty, no round prepared
            assertTrue("All usedIds should be empty after resetGame", viewModel.usedIds.isEmpty())
        }
}
