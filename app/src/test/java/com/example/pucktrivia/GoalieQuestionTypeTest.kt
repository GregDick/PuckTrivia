package com.example.pucktrivia

import com.example.pucktrivia.model.QuestionType
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

    private fun skaterUrl() =
        mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()

    private fun goalieUrl() =
        mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()

    private fun createViewModel(random: Random = Random(42)): TriviaViewModel =
        TriviaViewModel(OkHttpClient(), skaterUrl(), goalieUrl(), testDispatcher, random)

    /** Enqueues "{}" for the skater call, then the given goalie JSON. */
    private fun enqueueGoalieOnly(goalieJson: String) {
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setBody(goalieJson).setResponseCode(200))
    }

    // Standard 5 qualifying goalies (all have 10+ wins), distinct SV%
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
            advanceUntilIdle()

            assertNull(viewModel.pools[QuestionType.GOALIES_SAVE_PCT])
            assertEquals(3, viewModel.choices.size)
            assertNotNull(viewModel.correctPlayer)
        }

    // -----------------------------------------------------------------------
    // Story 2: Pool construction — minWins filter and poolFraction
    // -----------------------------------------------------------------------

    @Test
    fun `GOALIES_SAVE_PCT pool contains only goalies with minWins or more wins`() =
        runTest(testDispatcher) {
            // IDs 1,2,3 have 10+ wins (qualifying). IDs 4,5 have fewer than 10 (filtered out).
            val savePctg =
                listOf(
                    Triple(1, "G1", 0.930),
                    Triple(2, "G2", 0.920),
                    Triple(3, "G3", 0.915),
                    Triple(4, "G4", 0.910),
                    Triple(5, "G5", 0.905),
                )
            val wins =
                listOf(
                    Triple(1, "G1", 35.0),
                    Triple(2, "G2", 30.0),
                    Triple(3, "G3", 10.0),
                    Triple(4, "G4", 5.0),
                    Triple(5, "G5", 2.0),
                )
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.GOALIES_SAVE_PCT]!!
            val poolIds = pool.map { it.id }.toSet()
            assertEquals(
                "Pool should contain exactly 3 qualifying goalies",
                setOf(1, 2, 3),
                poolIds,
            )
        }

    @Test
    fun `GOALIES_SAVE_PCT pool includes all qualifying goalies (poolFraction = 1point0)`() =
        runTest(testDispatcher) {
            // 6 goalies all with 10+ wins → pool should contain all 6 (not just top 50%)
            val savePctg = (1..6).map { Triple(it, "G$it", 0.930 - it * 0.005) }
            val wins = (1..6).map { Triple(it, "G$it", (40 - it * 3).toDouble()) }
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.GOALIES_SAVE_PCT]!!
            assertEquals("All 6 qualifying goalies should be in the pool", 6, pool.size)
        }

    @Test
    fun `goalie with exactly minWins wins is included in pool`() =
        runTest(testDispatcher) {
            val savePctg =
                listOf(Triple(1, "G1", 0.930), Triple(2, "G2", 0.920), Triple(3, "G3", 0.915))
            val wins =
                listOf(
                    Triple(1, "G1", 20.0),
                    Triple(2, "G2", 15.0),
                    Triple(3, "G3", 10.0), // exactly minWins
                )
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.GOALIES_SAVE_PCT]!!
            assertTrue("Goalie with exactly 10 wins should be in pool", pool.any { it.id == 3 })
        }

    @Test
    fun `no goalie pool built when all goalies are below minWins`() =
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
            val savePctg =
                listOf(Triple(1, "G1", 0.930), Triple(2, "G2", 0.920), Triple(3, "G3", 0.915))
            val wins = listOf(Triple(1, "G1", 3.0), Triple(2, "G2", 2.0), Triple(3, "G3", 1.0))
            mockWebServer.enqueue(MockResponse().setBody(skaterJson).setResponseCode(200))
            mockWebServer.enqueue(
                MockResponse().setBody(createGoalieStatsJson(savePctg, wins)).setResponseCode(200)
            )
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertNull(viewModel.pools[QuestionType.GOALIES_SAVE_PCT])
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
            advanceUntilIdle()

            assertEquals("", viewModel.statUnitLabel)
        }

    @Test
    fun `goalie pool resets independently when exhausted`() =
        runTest(testDispatcher) {
            // 3 qualifying goalies → pool of 3. Round 1 exhausts it. Round 2 resets and reuses.
            val savePctg =
                listOf(Triple(1, "G1", 0.930), Triple(2, "G2", 0.920), Triple(3, "G3", 0.915))
            val wins = listOf(Triple(1, "G1", 20.0), Triple(2, "G2", 15.0), Triple(3, "G3", 10.0))
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            // Single type available (no skater data) → always GOALIES_SAVE_PCT
            val viewModel = createViewModel(Random(42))
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
            advanceUntilIdle()

            viewModel.choices.forEach { player ->
                assertTrue(
                    "displayValue '${player.displayValue}' should match 0.XXX pattern",
                    player.displayValue.matches(Regex("0\\.\\d{3}")),
                )
            }
        }
}
