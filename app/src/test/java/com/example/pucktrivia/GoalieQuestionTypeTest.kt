package com.example.pucktrivia

import com.example.pucktrivia.di.StatsUrlProvider
import com.example.pucktrivia.model.QuestionType
import com.example.pucktrivia.model.SeasonMode
import kotlin.random.Random
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalieQuestionTypeTest {

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

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun goalieJson(id: Int, firstName: String, lastName: String, value: Double) =
        """{"id":$id,"firstName":{"default":"$firstName"},"lastName":{"default":"$lastName"},"sweaterNumber":${id + 29},"teamAbbrev":"TST","position":"G","value":$value}"""

    /**
     * Builds a full goalie stats JSON response with savePctg and wins categories. Each triple is
     * (id, firstName, value).
     */
    private fun createGoalieStatsJson(
        savePctgGoalies: List<Triple<Int, String, Double>>,
        winsGoalies: List<Triple<Int, String, Double>>,
    ): String {
        val savePctgJson =
            savePctgGoalies.joinToString(",") { (id, name, value) ->
                goalieJson(id, name, "Goalie", value)
            }
        val winsJson =
            winsGoalies.joinToString(",") { (id, name, value) ->
                goalieJson(id, name, "Goalie", value)
            }
        return """{"savePctg":[$savePctgJson],"wins":[$winsJson]}"""
    }

    private fun fakeProvider(skaterUrl: String, goalieUrl: String): StatsUrlProvider =
        object : StatsUrlProvider() {
            override fun skaterUrl(mode: SeasonMode) = skaterUrl

            override fun goalieUrl(mode: SeasonMode) = goalieUrl
        }

    private fun createViewModel(random: Random = Random(42)): TriviaViewModel {
        val skaterUrl = mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
        val goalieUrl = mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()
        return TriviaViewModel(
            OkHttpClient(),
            fakeProvider(skaterUrl, goalieUrl),
            testDispatcher,
            random,
        )
    }

    /** Enqueues "{}" for the skater call, then the given goalie JSON. */
    private fun enqueueGoalieOnly(goalieJson: String) {
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setBody(goalieJson).setResponseCode(200))
    }

    // Standard 5 goalies with distinct SV%
    private val defaultSavePctg =
        listOf(
            Triple(1, "Fleury", 0.930),
            Triple(2, "Price", 0.920),
            Triple(3, "Rask", 0.915),
            Triple(4, "Quick", 0.910),
            Triple(5, "Rinne", 0.905),
        )
    private val defaultWins =
        listOf(
            Triple(1, "Fleury", 35.0),
            Triple(2, "Price", 30.0),
            Triple(3, "Rask", 28.0),
            Triple(4, "Quick", 25.0),
            Triple(5, "Rinne", 15.0),
        )

    // -----------------------------------------------------------------------
    // Story 1: Fetch and parse goalie data
    // -----------------------------------------------------------------------

    @Test
    fun `goalie data is stored in goalieStatsData under savePctg key`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertNotNull(viewModel.goalieStatsData["savePctg"])
            assertEquals(5, viewModel.goalieStatsData["savePctg"]!!.size)
        }

    @Test
    fun `goalie entries have correct fields`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val goalie = viewModel.goalieStatsData["savePctg"]!!.first()
            assertEquals(1, goalie.id)
            assertEquals("Fleury", goalie.firstName)
            assertEquals("Goalie", goalie.lastName)
            assertEquals("TST", goalie.teamAbbrev)
            assertEquals(0.930, goalie.value, 0.001)
        }

    @Test
    fun `goalieStatsData is populated with GoalieStatLeader entries for each category`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(
                "goalieStatsData should contain savePctg and wins keys",
                setOf("savePctg", "wins"),
                viewModel.goalieStatsData.keys,
            )
            assertEquals(5, viewModel.goalieStatsData["savePctg"]!!.size)
            assertEquals(5, viewModel.goalieStatsData["wins"]!!.size)
        }

    @Test
    fun `empty goalie response builds no goalie pool and game continues with skater types`() =
        runTest(testDispatcher) {
            val skaterJson =
                """{"points":[
                {"id":1,"firstName":{"default":"A"},"lastName":{"default":"P"},"sweaterNumber":11,"teamAbbrev":"TST","position":"C","value":100.0},
                {"id":2,"firstName":{"default":"B"},"lastName":{"default":"P"},"sweaterNumber":12,"teamAbbrev":"TST","position":"C","value":80.0},
                {"id":3,"firstName":{"default":"C"},"lastName":{"default":"P"},"sweaterNumber":13,"teamAbbrev":"TST","position":"C","value":60.0},
                {"id":4,"firstName":{"default":"D"},"lastName":{"default":"P"},"sweaterNumber":14,"teamAbbrev":"TST","position":"C","value":40.0},
                {"id":5,"firstName":{"default":"E"},"lastName":{"default":"P"},"sweaterNumber":15,"teamAbbrev":"TST","position":"C","value":20.0},
                {"id":6,"firstName":{"default":"F"},"lastName":{"default":"P"},"sweaterNumber":16,"teamAbbrev":"TST","position":"C","value":10.0}
            ]}"""
            mockWebServer.enqueue(MockResponse().setBody(skaterJson).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertNull(viewModel.pools[QuestionType.GOALIES_SAVE_PCT])
            assertEquals(3, viewModel.choices.size)
            assertNotNull(viewModel.correctPlayer)
        }

    // -----------------------------------------------------------------------
    // Story 2: Pool construction — poolFraction (no wins-based filter)
    // -----------------------------------------------------------------------

    @Test
    fun `GOALIES_SAVE_PCT pool includes all goalies regardless of wins`() =
        runTest(testDispatcher) {
            // 10 goalies with varied save percentages and varied wins from 0 to 30.
            // With minWins removed and poolFraction=1.0, all 10 should be in the pool.
            val savePctg = (1..10).map { Triple(it, "G$it", 0.930 - it * 0.002) }
            val wins = (1..10).map { Triple(it, "G$it", ((it - 1) * 3).toDouble()) }
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.GOALIES_SAVE_PCT]!!
            assertEquals("All 10 goalies should be in the pool", 10, pool.size)
            assertEquals(
                "Pool should include every goalie regardless of wins",
                (1..10).toSet(),
                pool.map { it.id }.toSet(),
            )
        }

    @Test
    fun `GOALIES_SAVE_PCT pool is sorted by save percentage descending`() =
        runTest(testDispatcher) {
            val savePctg = (1..10).map { Triple(it, "G$it", 0.930 - it * 0.002) }
            val wins = (1..10).map { Triple(it, "G$it", ((it - 1) * 3).toDouble()) }
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.GOALIES_SAVE_PCT]!!
            val values = pool.map { it.value }
            assertEquals(
                "Pool should be sorted by save percentage descending",
                values.sortedDescending(),
                values,
            )
        }

    @Test
    fun `GOALIES_SAVE_PCT pool size equals ceil(N times poolFraction)`() =
        runTest(testDispatcher) {
            val savePctg = (1..7).map { Triple(it, "G$it", 0.930 - it * 0.003) }
            val wins = (1..7).map { Triple(it, "G$it", (10 + it).toDouble()) }
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.GOALIES_SAVE_PCT]!!
            val expected = kotlin.math.ceil(7 * QuestionType.GOALIES_SAVE_PCT.poolFraction).toInt()
            assertEquals("Pool size should be ceil(N * poolFraction)", expected, pool.size)
        }

    @Test
    fun `GOALIES_SAVE_PCT pool with 4 playoff goalies includes all 4`() =
        runTest(testDispatcher) {
            // Early-playoff scenario: only 4 goalies have a recorded save percentage.
            // Without the old minWins=10 filter, all 4 should be in the pool.
            val savePctg =
                listOf(
                    Triple(1, "G1", 0.945),
                    Triple(2, "G2", 0.928),
                    Triple(3, "G3", 0.910),
                    Triple(4, "G4", 0.890),
                )
            val wins =
                listOf(
                    Triple(1, "G1", 4.0),
                    Triple(2, "G2", 3.0),
                    Triple(3, "G3", 2.0),
                    Triple(4, "G4", 1.0),
                )
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.Playoffs)
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.GOALIES_SAVE_PCT]!!
            assertEquals("All 4 playoff goalies should be in the pool", 4, pool.size)
        }

    // -----------------------------------------------------------------------
    // Story 3: Question mechanics
    // -----------------------------------------------------------------------

    @Test
    fun `correct answer is goalie with highest save percentage`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val maxValue = viewModel.choices.maxOf { it.value }
            assertEquals(maxValue, viewModel.correctPlayer!!.value, 0.001)
        }

    @Test
    fun `goalie choices have distinct save percentage values`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val values = viewModel.choices.map { it.value }
            assertEquals(
                "All goalie choice values must be distinct, but got: $values",
                values.size,
                values.distinct().size,
            )
        }

    @Test
    fun `goalie question text is correct`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(
                "Which of these goalies currently has the highest save percentage?",
                viewModel.questionText,
            )
        }

    @Test
    fun `goalie question has empty unit label`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals("", viewModel.statUnitLabel)
        }

    @Test
    fun `goalie pool resets independently when exhausted`() =
        runTest(testDispatcher) {
            // 3 goalies → pool of 3. Round 1 exhausts it. Round 2 resets and reuses.
            val savePctg =
                listOf(Triple(1, "G1", 0.930), Triple(2, "G2", 0.920), Triple(3, "G3", 0.915))
            val wins = listOf(Triple(1, "G1", 20.0), Triple(2, "G2", 15.0), Triple(3, "G3", 10.0))
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            // Single type available (no skater data) → always GOALIES_SAVE_PCT
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val round1Ids = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, round1Ids.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            val round2Ids = viewModel.choices.map { it.id }.toSet()
            assertTrue(
                "After pool reset, previously seen goalies should reappear",
                round1Ids.intersect(round2Ids).isNotEmpty(),
            )
        }

    @Test
    fun `displayValue of each goalie choice is formatted as 0 dot XXX`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.choices.forEach { player ->
                assertTrue(
                    "displayValue '${player.displayValue}' should match 0.XXX pattern",
                    player.displayValue.matches(Regex("0\\.\\d{3}")),
                )
            }
        }
}
