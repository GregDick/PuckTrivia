package com.example.pucktrivia

import com.example.pucktrivia.model.GoalieStatLeader
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
    fun `goalie entries are GoalieStatLeader instances`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val goalie = viewModel.goalieStatsData["savePctg"]!!.first()
            assertTrue(goalie is GoalieStatLeader)
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
}
