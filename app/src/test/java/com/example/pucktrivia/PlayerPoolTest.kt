package com.example.pucktrivia

import com.example.pucktrivia.di.StatsUrlProvider
import com.example.pucktrivia.model.PositionGroup
import com.example.pucktrivia.model.QuestionType
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.model.SkaterStatLeader
import com.example.pucktrivia.model.positionGroup
import kotlin.math.ceil
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the four-pool position-based architecture:
 * - Position group classification (PositionGroup enum and positionGroup() extension)
 * - Pool construction: position filtering before top-50% cut
 * - Round selection: correct pool used for each QuestionType
 * - Independent per-type used-player tracking
 * - Per-type pool reset
 *
 * Test data uses 6 forwards ("C") and 6 defenders ("D") per stat type unless otherwise noted,
 * producing 4 pools of 3 players each. With 4 available types, type selection calls
 * random.nextInt(4). Pool shuffle calls random.nextInt(3) and random.nextInt(2) — never
 * random.nextInt(4) — so intercepting nextInt(until == 4) uniquely controls type selection.
 *
 * QuestionType indices (order from enum definition): 0 = DEFENDERS_POINTS, 1 = FORWARDS_POINTS, 2 =
 * DEFENDERS_GOALS, 3 = FORWARDS_GOALS
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerPoolTest {

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

    private fun player(id: Int, name: String, position: String, value: Double) =
        """{"id":$id,"firstName":{"default":"$name"},"lastName":{"default":"Player"},"sweaterNumber":${id + 10},"teamAbbrev":"TST","position":"$position","value":$value}"""

    /**
     * Standard mixed-position dataset: 6 forwards (C) + 6 defenders (D) per stat type. Produces 4
     * pools of 3 players each (ceil(6/2)=3).
     *
     * FORWARDS_POINTS pool: IDs 1,2,3 (values 100,90,80) DEFENDERS_POINTS pool: IDs 11,12,13
     * (values 95,85,75) FORWARDS_GOALS pool: IDs 21,22,23 (values 50,45,40) DEFENDERS_GOALS pool:
     * IDs 31,32,33 (values 55,48,42)
     */
    private fun createMixedJson(): String {
        val pointsForwards =
            listOf(
                player(1, "FP1", "C", 100.0),
                player(2, "FP2", "C", 90.0),
                player(3, "FP3", "C", 80.0),
                player(4, "FP4", "C", 70.0),
                player(5, "FP5", "C", 60.0),
                player(6, "FP6", "C", 50.0),
            )
        val pointsDefenders =
            listOf(
                player(11, "DP1", "D", 95.0),
                player(12, "DP2", "D", 85.0),
                player(13, "DP3", "D", 75.0),
                player(14, "DP4", "D", 65.0),
                player(15, "DP5", "D", 55.0),
                player(16, "DP6", "D", 45.0),
            )
        val goalsForwards =
            listOf(
                player(21, "FG1", "C", 50.0),
                player(22, "FG2", "C", 45.0),
                player(23, "FG3", "C", 40.0),
                player(24, "FG4", "C", 35.0),
                player(25, "FG5", "C", 30.0),
                player(26, "FG6", "C", 25.0),
            )
        val goalsDefenders =
            listOf(
                player(31, "DG1", "D", 55.0),
                player(32, "DG2", "D", 48.0),
                player(33, "DG3", "D", 42.0),
                player(34, "DG4", "D", 36.0),
                player(35, "DG5", "D", 28.0),
                player(36, "DG6", "D", 20.0),
            )
        val pts = (pointsForwards + pointsDefenders).joinToString(",")
        val gls = (goalsForwards + goalsDefenders).joinToString(",")
        return """{"points":[$pts],"goals":[$gls]}"""
    }

    /** Points-only JSON with mixed positions (no goals key). */
    private fun createPointsOnlyMixedJson(): String {
        val pts =
            listOf(
                    player(1, "FP1", "C", 100.0),
                    player(2, "FP2", "C", 90.0),
                    player(3, "FP3", "C", 80.0),
                    player(4, "FP4", "C", 70.0),
                    player(5, "FP5", "C", 60.0),
                    player(6, "FP6", "C", 50.0),
                    player(11, "DP1", "D", 95.0),
                    player(12, "DP2", "D", 85.0),
                    player(13, "DP3", "D", 75.0),
                    player(14, "DP4", "D", 65.0),
                    player(15, "DP5", "D", 55.0),
                    player(16, "DP6", "D", 45.0),
                )
                .joinToString(",")
        return """{"points":[$pts]}"""
    }

    private fun fakeProvider(skaterUrl: String, goalieUrl: String): StatsUrlProvider =
        object : StatsUrlProvider {
            override fun skaterUrl(mode: SeasonMode) = skaterUrl

            override fun goalieUrl(mode: SeasonMode) = goalieUrl
        }

    private fun createViewModel(random: Random): TriviaViewModel {
        val skaterUrl = mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
        val goalieUrl = mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()
        return TriviaViewModel(
            OkHttpClient(),
            fakeProvider(skaterUrl, goalieUrl),
            FakeHighScoreRepository(),
            FixedTimeProvider(),
            testDispatcher,
            random = random,
        )
    }

    /**
     * Controls type selection by intercepting random.nextInt(4) calls (type selection). Pool size
     * is 3, so shuffle only calls nextInt(3) and nextInt(2) — never nextInt(4). This uniquely
     * identifies type-selection calls without interfering with shuffle.
     */
    private fun makeTypeControlledRandom(typeIndices: List<Int>): Random {
        val delegate = Random(42)
        val it = typeIndices.iterator()
        return object : Random() {
            override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

            override fun nextInt(until: Int): Int {
                if (until == 4 && it.hasNext()) return it.next()
                return delegate.nextInt(until)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Position Group Classification (Story 1)
    // -----------------------------------------------------------------------

    @Test
    fun `position D maps to DEFENDERS`() {
        val p = SkaterStatLeader(1, "A", "B", null, "TST", "D", 10.0)
        assertEquals(PositionGroup.DEFENDERS, p.positionGroup())
    }

    @Test
    fun `position C maps to FORWARDS`() {
        val p = SkaterStatLeader(1, "A", "B", null, "TST", "C", 10.0)
        assertEquals(PositionGroup.FORWARDS, p.positionGroup())
    }

    @Test
    fun `position L maps to FORWARDS`() {
        val p = SkaterStatLeader(1, "A", "B", null, "TST", "L", 10.0)
        assertEquals(PositionGroup.FORWARDS, p.positionGroup())
    }

    @Test
    fun `position R maps to FORWARDS`() {
        val p = SkaterStatLeader(1, "A", "B", null, "TST", "R", 10.0)
        assertEquals(PositionGroup.FORWARDS, p.positionGroup())
    }

    @Test
    fun `unknown position G returns null`() {
        val p = SkaterStatLeader(1, "A", "B", null, "TST", "G", 10.0)
        assertNull(p.positionGroup())
    }

    @Test
    fun `unknown position X returns null`() {
        val p = SkaterStatLeader(1, "A", "B", null, "TST", "X", 10.0)
        assertNull(p.positionGroup())
    }

    @Test
    fun `no player maps to both groups`() {
        val positions = listOf("D", "C", "L", "R", "G", "X", "LW", "RW")
        for (pos in positions) {
            val p = SkaterStatLeader(1, "A", "B", null, "TST", pos, 10.0)
            val group = p.positionGroup()
            assertTrue(
                "Position '$pos' should map to at most one group",
                group == null || group == PositionGroup.DEFENDERS || group == PositionGroup.FORWARDS,
            )
            if (group != null) {
                assertTrue(
                    "D must be DEFENDERS only",
                    pos != "D" || group == PositionGroup.DEFENDERS,
                )
                assertTrue(
                    "C/L/R must be FORWARDS only",
                    pos !in listOf("C", "L", "R") || group == PositionGroup.FORWARDS,
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Pool Construction (Story 2)
    // -----------------------------------------------------------------------

    @Test
    fun `forwards points pool contains top 50% of forwards from points leaderboard`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody(createMixedJson()).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.FORWARDS_POINTS]!!
            assertEquals("Forwards points pool should have ceil(6/2)=3 players", 3, pool.size)
            val poolIds = pool.map { it.id }.toSet()
            assertEquals(setOf(1, 2, 3), poolIds)
        }

    @Test
    fun `defenders points pool contains top 50% of defenders from points leaderboard`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody(createMixedJson()).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.DEFENDERS_POINTS]!!
            assertEquals("Defenders points pool should have ceil(6/2)=3 players", 3, pool.size)
            val poolIds = pool.map { it.id }.toSet()
            assertEquals(setOf(11, 12, 13), poolIds)
        }

    @Test
    fun `forwards goals pool contains top 50% of forwards from goals leaderboard`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody(createMixedJson()).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.FORWARDS_GOALS]!!
            assertEquals("Forwards goals pool should have ceil(6/2)=3 players", 3, pool.size)
            val poolIds = pool.map { it.id }.toSet()
            assertEquals(setOf(21, 22, 23), poolIds)
        }

    @Test
    fun `defenders goals pool contains top 50% of defenders from goals leaderboard`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody(createMixedJson()).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.DEFENDERS_GOALS]!!
            assertEquals("Defenders goals pool should have ceil(6/2)=3 players", 3, pool.size)
            val poolIds = pool.map { it.id }.toSet()
            assertEquals(setOf(31, 32, 33), poolIds)
        }

    @Test
    fun `pool construction filters by position before applying top 50 percent`() =
        runTest(testDispatcher) {
            // 3 forwards + 3 defenders in points → 2 forwards pool (ceil(3/2)=2), 2 defenders pool
            val pts =
                listOf(
                        player(1, "F1", "C", 100.0),
                        player(2, "F2", "C", 80.0),
                        player(3, "F3", "C", 60.0),
                        player(11, "D1", "D", 90.0),
                        player(12, "D2", "D", 70.0),
                        player(13, "D3", "D", 50.0),
                    )
                    .joinToString(",")
            mockWebServer.enqueue(
                MockResponse().setBody("""{"points":[$pts]}""").setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val fwdPool = viewModel.pools[QuestionType.FORWARDS_POINTS]!!
            val defPool = viewModel.pools[QuestionType.DEFENDERS_POINTS]!!
            assertEquals("Forwards pool: ceil(3/2)=2", 2, fwdPool.size)
            assertEquals("Defenders pool: ceil(3/2)=2", 2, defPool.size)
            // Forwards pool contains only forwards
            assertTrue(
                "Forwards pool should contain only forwards",
                fwdPool.filterIsInstance<SkaterStatLeader>().all { it.position == "C" },
            )
            // Defenders pool contains only defenders
            assertTrue(
                "Defenders pool should contain only defenders",
                defPool.filterIsInstance<SkaterStatLeader>().all { it.position == "D" },
            )
        }

    @Test
    fun `asymmetric position distribution splits correctly`() =
        runTest(testDispatcher) {
            // 3 defenders + 17 forwards in goals → defenders pool: 2, forwards pool: 9
            val gls =
                (1..3).map { player(it, "D$it", "D", (20 - it).toDouble()) } +
                    (11..27).map { player(it, "F${it - 10}", "C", (40 - it + 11).toDouble()) }
            mockWebServer.enqueue(
                MockResponse()
                    .setBody("""{"goals":[${gls.joinToString(",")}]}""")
                    .setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val defPool = viewModel.pools[QuestionType.DEFENDERS_GOALS]!!
            val fwdPool = viewModel.pools[QuestionType.FORWARDS_GOALS]!!
            assertEquals("Defenders goals pool: ceil(3/2)=2", 2, defPool.size)
            assertEquals("Forwards goals pool: ceil(17/2)=9", 9, fwdPool.size)
        }

    @Test
    fun `pool construction preserves tied values without dedup`() =
        runTest(testDispatcher) {
            // 6 forwards in points: two tied at 80.0. Top 50% = 3 players including both tied.
            val pts =
                listOf(
                        player(1, "F1", "C", 100.0),
                        player(2, "F2", "C", 80.0),
                        player(3, "F3", "C", 80.0),
                        player(4, "F4", "C", 60.0),
                        player(5, "F5", "C", 40.0),
                        player(6, "F6", "C", 20.0),
                        player(11, "D1", "D", 90.0),
                        player(12, "D2", "D", 70.0),
                        player(13, "D3", "D", 50.0),
                        player(14, "D4", "D", 30.0),
                        player(15, "D5", "D", 10.0),
                        player(16, "D6", "D", 5.0),
                    )
                    .joinToString(",")
            mockWebServer.enqueue(
                MockResponse().setBody("""{"points":[$pts]}""").setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.FORWARDS_POINTS]!!
            assertEquals(3, pool.size)
            val poolIds = pool.map { it.id }.toSet()
            assertTrue("Pool should contain player 2 (tied value)", 2 in poolIds)
            assertTrue("Pool should contain player 3 (tied value)", 3 in poolIds)
        }

    @Test
    fun `odd sized group rounds up correctly for pool size`() =
        runTest(testDispatcher) {
            // 7 forwards → ceil(7/2)=4
            val pts =
                (1..7).map { player(it, "F$it", "C", (100 - it * 5).toDouble()) } +
                    (11..16).map { player(it, "D${it - 10}", "D", (80 - (it - 11) * 5).toDouble()) }
            mockWebServer.enqueue(
                MockResponse()
                    .setBody("""{"points":[${pts.joinToString(",")}]}""")
                    .setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.FORWARDS_POINTS]!!
            assertEquals(
                "7 forwards → pool of ${ceil(7 / 2.0).toInt()} (rounded up)",
                ceil(7 / 2.0).toInt(),
                pool.size,
            )
        }

    @Test
    fun `missing goals key produces no goals pools`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse().setBody(createPointsOnlyMixedJson()).setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertNull(
                "FORWARDS_GOALS pool should be absent when goals key is absent",
                viewModel.pools[QuestionType.FORWARDS_GOALS],
            )
            assertNull(
                "DEFENDERS_GOALS pool should be absent when goals key is absent",
                viewModel.pools[QuestionType.DEFENDERS_GOALS],
            )
            assertTrue(
                "FORWARDS_POINTS pool should still be built",
                viewModel.pools[QuestionType.FORWARDS_POINTS]!!.isNotEmpty(),
            )
            assertTrue(
                "DEFENDERS_POINTS pool should still be built",
                viewModel.pools[QuestionType.DEFENDERS_POINTS]!!.isNotEmpty(),
            )
        }

    @Test
    fun `same player in both leaderboards appears in both corresponding pools`() =
        runTest(testDispatcher) {
            // Defenders 11,12,13 appear in both points and goals leaderboards
            val pts =
                listOf(
                        player(1, "F1", "C", 100.0),
                        player(2, "F2", "C", 90.0),
                        player(3, "F3", "C", 80.0),
                        player(4, "F4", "C", 70.0),
                        player(5, "F5", "C", 60.0),
                        player(6, "F6", "C", 50.0),
                        player(11, "D1", "D", 95.0),
                        player(12, "D2", "D", 85.0),
                        player(13, "D3", "D", 75.0),
                        player(14, "D4", "D", 65.0),
                        player(15, "D5", "D", 55.0),
                        player(16, "D6", "D", 45.0),
                    )
                    .joinToString(",")
            val gls =
                listOf(
                        player(11, "D1", "D", 50.0),
                        player(12, "D2", "D", 45.0),
                        player(13, "D3", "D", 40.0),
                        player(14, "D4", "D", 35.0),
                        player(15, "D5", "D", 30.0),
                        player(16, "D6", "D", 25.0),
                        player(21, "FG1", "C", 48.0),
                        player(22, "FG2", "C", 43.0),
                        player(23, "FG3", "C", 38.0),
                        player(24, "FG4", "C", 33.0),
                        player(25, "FG5", "C", 28.0),
                        player(26, "FG6", "C", 22.0),
                    )
                    .joinToString(",")
            mockWebServer.enqueue(
                MockResponse().setBody("""{"points":[$pts],"goals":[$gls]}""").setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val defPointsIds =
                viewModel.pools[QuestionType.DEFENDERS_POINTS]!!.map { it.id }.toSet()
            val defGoalsIds = viewModel.pools[QuestionType.DEFENDERS_GOALS]!!.map { it.id }.toSet()
            assertTrue(
                "Player 11 should be in both defenders-points and defenders-goals pools",
                11 in defPointsIds && 11 in defGoalsIds,
            )
        }

    // -----------------------------------------------------------------------
    // Round Selection
    // -----------------------------------------------------------------------

    @Test
    fun `choices in a defenders question are all defenders`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody(createMixedJson()).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Force DEFENDERS_POINTS (index 0)
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(0)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(
                "Which of these defenders currently has the most points?",
                viewModel.questionText,
            )
            assertTrue(
                "All choices in a defenders question must be defenders",
                viewModel.choices.filterIsInstance<SkaterStatLeader>().all { it.position == "D" },
            )
        }

    @Test
    fun `choices in a forwards question are all forwards`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody(createMixedJson()).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Force FORWARDS_POINTS (index 1)
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(1)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(
                "Which of these forwards currently has the most points?",
                viewModel.questionText,
            )
            assertTrue(
                "All choices in a forwards question must be forwards",
                viewModel.choices.filterIsInstance<SkaterStatLeader>().all { it.position == "C" },
            )
        }

    @Test
    fun `three choices always have distinct stat values`() =
        runTest(testDispatcher) {
            // 8 forwards in points (pool of 4 with a tie at 80.0 in the pool)
            val pts =
                listOf(
                        player(1, "F1", "C", 100.0),
                        player(2, "F2", "C", 80.0),
                        player(3, "F3", "C", 80.0),
                        player(4, "F4", "C", 60.0),
                        player(5, "F5", "C", 40.0),
                        player(6, "F6", "C", 20.0),
                        player(7, "F7", "C", 10.0),
                        player(8, "F8", "C", 5.0),
                        player(11, "D1", "D", 95.0),
                        player(12, "D2", "D", 85.0),
                        player(13, "D3", "D", 75.0),
                        player(14, "D4", "D", 65.0),
                        player(15, "D5", "D", 55.0),
                        player(16, "D6", "D", 45.0),
                    )
                    .joinToString(",")
            mockWebServer.enqueue(
                MockResponse().setBody("""{"points":[$pts]}""").setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Force FORWARDS_POINTS (index 0, since only 2 types available with no goals key)
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val values = viewModel.choices.map { it.value }
            assertEquals(3, values.size)
            assertEquals(
                "All choice values must be distinct, but got: $values",
                values.size,
                values.distinct().size,
            )
        }

    @Test
    fun `correct answer is highest value among the three choices`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody(createMixedJson()).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val maxValue = viewModel.choices.maxOf { it.value }
            assertEquals(
                "Correct player should have the highest value among choices",
                maxValue,
                viewModel.correctPlayer!!.value,
                0.001,
            )
        }

    // -----------------------------------------------------------------------
    // Pool Viability — unviable pools trigger fatalError when selected
    // -----------------------------------------------------------------------

    @Test
    fun `selecting an unviable pool triggers fatalError`() =
        runTest(testDispatcher) {
            // 6 defenders all tied at 10.0 → pool is unviable (only 1 distinct value).
            // No forwards in data → FORWARDS_POINTS pool is never built.
            // Only pool key is DEFENDERS_POINTS, so it is always selected.
            val pts =
                listOf(
                        player(11, "D1", "D", 10.0),
                        player(12, "D2", "D", 10.0),
                        player(13, "D3", "D", 10.0),
                        player(14, "D4", "D", 10.0),
                        player(15, "D5", "D", 10.0),
                        player(16, "D6", "D", 10.0),
                    )
                    .joinToString(",")
            mockWebServer.enqueue(
                MockResponse().setBody("""{"points":[$pts]}""").setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertTrue(viewModel.fatalError)
            assertEquals(0, viewModel.choices.size)
            assertNull(viewModel.correctPlayer)
        }

    @Test
    fun `when no type has 3 distinct values fatalError is triggered`() =
        runTest(testDispatcher) {
            // Every pool is unviable: forwards have only 2 players, defenders all tied.
            val pts =
                listOf(
                        player(1, "F1", "C", 100.0),
                        player(2, "F2", "C", 90.0),
                        player(11, "D1", "D", 10.0),
                        player(12, "D2", "D", 10.0),
                        player(13, "D3", "D", 10.0),
                        player(14, "D4", "D", 10.0),
                    )
                    .joinToString(",")
            mockWebServer.enqueue(
                MockResponse().setBody("""{"points":[$pts]}""").setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertTrue(viewModel.fatalError)
            assertEquals(0, viewModel.choices.size)
            assertNull(viewModel.correctPlayer)
        }

    // -----------------------------------------------------------------------
    // Independent Used-Player Tracking (Story 4)
    // -----------------------------------------------------------------------

    @Test
    fun `player used in forwards points question CAN appear in forwards goals question`() =
        runTest(testDispatcher) {
            // Players 1,2,3 appear in both points and goals leaderboards as forwards
            val pts =
                listOf(
                        player(1, "F1", "C", 100.0),
                        player(2, "F2", "C", 90.0),
                        player(3, "F3", "C", 80.0),
                        player(4, "F4", "C", 70.0),
                        player(5, "F5", "C", 60.0),
                        player(6, "F6", "C", 50.0),
                        player(11, "D1", "D", 95.0),
                        player(12, "D2", "D", 85.0),
                        player(13, "D3", "D", 75.0),
                        player(14, "D4", "D", 65.0),
                        player(15, "D5", "D", 55.0),
                        player(16, "D6", "D", 45.0),
                    )
                    .joinToString(",")
            val gls =
                listOf(
                        player(1, "F1", "C", 50.0),
                        player(2, "F2", "C", 45.0),
                        player(3, "F3", "C", 40.0),
                        player(4, "F4", "C", 35.0),
                        player(5, "F5", "C", 30.0),
                        player(6, "F6", "C", 25.0),
                        player(11, "D1", "D", 55.0),
                        player(12, "D2", "D", 48.0),
                        player(13, "D3", "D", 42.0),
                        player(14, "D4", "D", 36.0),
                        player(15, "D5", "D", 28.0),
                        player(16, "D6", "D", 20.0),
                    )
                    .joinToString(",")
            mockWebServer.enqueue(
                MockResponse().setBody("""{"points":[$pts],"goals":[$gls]}""").setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Round 1 = FORWARDS_POINTS (1), Round 2 = FORWARDS_GOALS (3)
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(1, 3)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val pointsChoiceIds = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, pointsChoiceIds.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Goals round can reuse players 1,2,3 since cross-type reuse is allowed
            val goalsChoiceIds = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, goalsChoiceIds.size)
            val overlap = pointsChoiceIds.intersect(goalsChoiceIds)
            assertTrue(
                "Cross-type reuse should be allowed: points choices $pointsChoiceIds should " +
                    "overlap with goals choices $goalsChoiceIds (shared players 1,2,3)",
                overlap.isNotEmpty(),
            )
        }

    @Test
    fun `player used in goals question CANNOT appear in another goals question until reset`() =
        runTest(testDispatcher) {
            // 12 forwards in goals → FORWARDS_GOALS pool of 6. Round 1 uses 3, round 2 picks from
            // remaining 3 (no reset needed, no overlap).
            val gls = (1..12).map { player(it, "FG$it", "C", (60 - it * 5).toDouble()) }
            val pts =
                listOf(
                    player(51, "FP1", "C", 100.0),
                    player(52, "FP2", "C", 90.0),
                    player(53, "FP3", "C", 80.0),
                    player(54, "FP4", "C", 70.0),
                    player(55, "FP5", "C", 60.0),
                    player(56, "FP6", "C", 50.0),
                    player(61, "DP1", "D", 95.0),
                    player(62, "DP2", "D", 85.0),
                    player(63, "DP3", "D", 75.0),
                    player(64, "DP4", "D", 65.0),
                    player(65, "DP5", "D", 55.0),
                    player(66, "DP6", "D", 45.0),
                )
            mockWebServer.enqueue(
                MockResponse()
                    .setBody(
                        """{"points":[${pts.joinToString(",")}],"goals":[${gls.joinToString(",")}]}"""
                    )
                    .setResponseCode(200)
            )
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Both rounds = FORWARDS_GOALS (index 3, since 4 types available)
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(3, 3)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val round1Ids = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, round1Ids.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            val round2Ids = viewModel.choices.map { it.id }.toSet()
            val overlap = round1Ids.intersect(round2Ids)
            assertTrue(
                "Same-type reuse is forbidden until reset. Round 1: $round1Ids, Round 2: $round2Ids, overlap: $overlap",
                overlap.isEmpty(),
            )
        }

    @Test
    fun `per-type used sets are independent across all four types`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody(createMixedJson()).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Round 1 = FORWARDS_POINTS (1), Round 2 = FORWARDS_GOALS (3)
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(1, 3)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            // After round 1 (FORWARDS_POINTS): only that type has used IDs
            assertEquals(3, (viewModel.usedIds[QuestionType.FORWARDS_POINTS] ?: emptySet()).size)
            assertEquals(0, (viewModel.usedIds[QuestionType.FORWARDS_GOALS] ?: emptySet()).size)
            assertEquals(0, (viewModel.usedIds[QuestionType.DEFENDERS_POINTS] ?: emptySet()).size)
            assertEquals(0, (viewModel.usedIds[QuestionType.DEFENDERS_GOALS] ?: emptySet()).size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // After round 2 (FORWARDS_GOALS): two types have used IDs, others still empty
            assertEquals(3, (viewModel.usedIds[QuestionType.FORWARDS_POINTS] ?: emptySet()).size)
            assertEquals(3, (viewModel.usedIds[QuestionType.FORWARDS_GOALS] ?: emptySet()).size)
            assertEquals(0, (viewModel.usedIds[QuestionType.DEFENDERS_POINTS] ?: emptySet()).size)
            assertEquals(0, (viewModel.usedIds[QuestionType.DEFENDERS_GOALS] ?: emptySet()).size)
        }

    // -----------------------------------------------------------------------
    // Per-Type Reset (Story 4)
    // -----------------------------------------------------------------------

    @Test
    fun `forwards goals pool resets independently without resetting other types`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody(createMixedJson()).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Sequence: FORWARDS_POINTS(1), FORWARDS_GOALS(3), FORWARDS_GOALS(3) again (exhausted)
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(1, 3, 3)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            // Round 1 (FORWARDS_POINTS)
            val fwdPtsUsedR1 =
                (viewModel.usedIds[QuestionType.FORWARDS_POINTS] ?: emptySet()).toSet()
            assertEquals(3, fwdPtsUsedR1.size)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 2 (FORWARDS_GOALS) — exhausts goals pool
            assertEquals(3, (viewModel.usedIds[QuestionType.FORWARDS_GOALS] ?: emptySet()).size)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 3 (FORWARDS_GOALS again) — goals resets, points unaffected
            assertEquals(3, viewModel.choices.size)
            assertTrue(
                "FORWARDS_POINTS used set should NOT be reset when FORWARDS_GOALS resets",
                (viewModel.usedIds[QuestionType.FORWARDS_POINTS] ?: emptySet()).containsAll(
                    fwdPtsUsedR1
                ),
            )
        }

    @Test
    fun `after per-type reset previously seen players may reappear in that type`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody(createMixedJson()).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Two consecutive FORWARDS_GOALS (3) rounds — pool of 3 exhausted after round 1
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(3, 3)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val round1Ids = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, round1Ids.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            val round2Ids = viewModel.choices.map { it.id }.toSet()
            val overlap = round1Ids.intersect(round2Ids)
            assertTrue(
                "After per-type reset, previously seen players should reappear. " +
                    "Round 1: $round1Ids, Round 2: $round2Ids",
                overlap.isNotEmpty(),
            )
        }

    @Test
    fun `resetGame clears all four used sets`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setBody(createMixedJson()).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Play two rounds of different types to accumulate used IDs
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(0, 1, 2, 3)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            viewModel.resetGame()

            // resetGame() returns to Start Screen — all used sets are cleared, no round prepared
            assertTrue("All usedIds should be empty after resetGame", viewModel.usedIds.isEmpty())
        }
}
