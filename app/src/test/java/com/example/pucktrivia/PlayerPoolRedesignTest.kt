package com.example.pucktrivia

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
 * Tests for the player pool redesign: independent per-type pools, top-50% pool construction,
 * per-type used-player tracking, and per-type pool reset.
 *
 * These tests assert the NEW independent-pool behavior and should FAIL against the current
 * production code which uses a shared pool with global resets.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerPoolRedesignTest {

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

    /** Creates JSON with both "points" and "goals" keys. */
    private fun createStatsJsonWithGoals(
        pointsPlayers: List<Triple<Int, String, Double>>,
        goalsPlayers: List<Triple<Int, String, Double>>,
    ): String {
        fun playersToJson(players: List<Triple<Int, String, Double>>): String =
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
        return """{"points": [${playersToJson(pointsPlayers)}], "goals": [${playersToJson(goalsPlayers)}]}"""
    }

    /** Creates JSON with only "points" key (no goals data). */
    private fun createPointsOnlyJson(players: List<Triple<Int, String, Double>>): String {
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

    private fun mockUrl(): String =
        mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()

    private fun createViewModel(random: Random): TriviaViewModel {
        return TriviaViewModel(OkHttpClient(), mockUrl(), testDispatcher, random)
    }

    // =======================================================================
    // Pool Construction
    // =======================================================================

    @Test
    fun `points pool contains top 50 percent of points leaderboard`() =
        runTest(testDispatcher) {
            // Arrange - 6 points players, pool should be top 3
            val pointsPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 60.0),
                    Triple(4, "Dave", 40.0),
                    Triple(5, "Eve", 20.0),
                    Triple(6, "Frank", 10.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(10, "GoalA", 50.0),
                    Triple(11, "GoalB", 40.0),
                    Triple(12, "GoalC", 30.0),
                    Triple(13, "GoalD", 20.0),
                    Triple(14, "GoalE", 10.0),
                    Triple(15, "GoalF", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            advanceUntilIdle()

            // Assert - pointsPool should exist and have ceil(6/2) = 3 players
            val expectedSize = ceil(6 / 2.0).toInt()
            assertEquals(
                "Points pool should contain top 50% ($expectedSize players)",
                expectedSize,
                viewModel.pointsPool.size,
            )
            // Top 3 by value should be IDs 1, 2, 3
            val poolIds = viewModel.pointsPool.map { it.id }.toSet()
            assertEquals(setOf(1, 2, 3), poolIds)
        }

    @Test
    fun `goals pool contains top 50 percent of goals leaderboard`() =
        runTest(testDispatcher) {
            // Arrange - 6 goals players, pool should be top 3
            val pointsPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 60.0),
                    Triple(4, "Dave", 40.0),
                    Triple(5, "Eve", 20.0),
                    Triple(6, "Frank", 10.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(10, "GoalA", 50.0),
                    Triple(11, "GoalB", 40.0),
                    Triple(12, "GoalC", 30.0),
                    Triple(13, "GoalD", 20.0),
                    Triple(14, "GoalE", 10.0),
                    Triple(15, "GoalF", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            advanceUntilIdle()

            // Assert - goalsPool should exist and have ceil(6/2) = 3 players
            val expectedSize = ceil(6 / 2.0).toInt()
            assertEquals(
                "Goals pool should contain top 50% ($expectedSize players)",
                expectedSize,
                viewModel.goalsPool!!.size,
            )
            // Top 3 by value should be IDs 10, 11, 12
            val poolIds = viewModel.goalsPool!!.map { it.id }.toSet()
            assertEquals(setOf(10, 11, 12), poolIds)
        }

    @Test
    fun `pool construction preserves players with tied values no dedup`() =
        runTest(testDispatcher) {
            // Arrange - 6 points players, two tied at 80.0. Top 50% = 3 players.
            // All 3 should be kept even though two share the same value.
            val pointsPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 80.0),
                    Triple(4, "Dave", 60.0),
                    Triple(5, "Eve", 40.0),
                    Triple(6, "Frank", 20.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(10, "GoalA", 50.0),
                    Triple(11, "GoalB", 40.0),
                    Triple(12, "GoalC", 30.0),
                    Triple(13, "GoalD", 20.0),
                    Triple(14, "GoalE", 10.0),
                    Triple(15, "GoalF", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            advanceUntilIdle()

            // Assert - pool has 3 players and both tied players (IDs 2 and 3) are present
            assertEquals(3, viewModel.pointsPool.size)
            val poolIds = viewModel.pointsPool.map { it.id }.toSet()
            assertTrue("Pool should contain player 2 (tied value)", 2 in poolIds)
            assertTrue("Pool should contain player 3 (tied value)", 3 in poolIds)
        }

    @Test
    fun `odd sized list rounds up correctly for pool size`() =
        runTest(testDispatcher) {
            // Arrange - 7 players, ceil(7/2) = 4
            val pointsPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 90.0),
                    Triple(3, "Carol", 80.0),
                    Triple(4, "Dave", 70.0),
                    Triple(5, "Eve", 60.0),
                    Triple(6, "Frank", 50.0),
                    Triple(7, "Grace", 40.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(10, "GoalA", 50.0),
                    Triple(11, "GoalB", 40.0),
                    Triple(12, "GoalC", 30.0),
                    Triple(13, "GoalD", 20.0),
                    Triple(14, "GoalE", 10.0),
                    Triple(15, "GoalF", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            advanceUntilIdle()

            // Assert - pool should have 4 players
            val expectedSize = ceil(7 / 2.0).toInt()
            assertEquals(
                "7 players -> pool of $expectedSize (rounded up)",
                expectedSize,
                viewModel.pointsPool.size,
            )
        }

    @Test
    fun `missing goals key produces no goals pool`() =
        runTest(testDispatcher) {
            // Arrange - only points data, no goals key
            val json =
                createPointsOnlyJson(
                    listOf(
                        Triple(1, "Alice", 100.0),
                        Triple(2, "Bob", 80.0),
                        Triple(3, "Carol", 60.0),
                        Triple(4, "Dave", 40.0),
                        Triple(5, "Eve", 20.0),
                        Triple(6, "Frank", 10.0),
                    )
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            advanceUntilIdle()

            // Assert
            assertNull("Goals pool should be null when goals key is absent", viewModel.goalsPool)
            assertTrue("Points pool should still be built", viewModel.pointsPool.isNotEmpty())
        }

    // =======================================================================
    // Round Selection (no-tie greedy pick)
    // =======================================================================

    @Test
    fun `three choices always have distinct stat values`() =
        runTest(testDispatcher) {
            // Arrange - force a points question, pool has players with some tied values
            // 8 players -> pool of 4, with a tie at 80.0 in the pool
            val seed = generateSequence(0) { it + 1 }.first { !Random(it).nextBoolean() }
            val pointsPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 80.0),
                    Triple(4, "Dave", 60.0),
                    Triple(5, "Eve", 40.0),
                    Triple(6, "Frank", 20.0),
                    Triple(7, "Grace", 10.0),
                    Triple(8, "Hank", 5.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(10, "GoalA", 50.0),
                    Triple(11, "GoalB", 40.0),
                    Triple(12, "GoalC", 30.0),
                    Triple(13, "GoalD", 20.0),
                    Triple(14, "GoalE", 10.0),
                    Triple(15, "GoalF", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Assert
            val values = viewModel.choices.map { it.value }
            assertEquals(3, values.size)
            assertEquals(
                "All choice values must be distinct, but got: $values",
                values.size,
                values.distinct().size,
            )
        }

    @Test
    fun `players with tied values can appear in different rounds`() =
        runTest(testDispatcher) {
            // Arrange - pool has two players tied at 80.0. Over multiple rounds, both should
            // eventually appear (they are not permanently excluded by dedup).
            // Use 6 points players -> pool of 3. Two of the pool entries share value 80.0.
            // With only 3 players in pool and 2 sharing a value, the greedy pick can only
            // choose one of the tied pair per round. After exhausting, reset lets the other appear.
            val seed = generateSequence(0) { it + 1 }.first { !Random(it).nextBoolean() }
            val pointsPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 80.0),
                    Triple(4, "Dave", 60.0),
                    Triple(5, "Eve", 40.0),
                    Triple(6, "Frank", 20.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(10, "GoalA", 50.0),
                    Triple(11, "GoalB", 40.0),
                    Triple(12, "GoalC", 30.0),
                    Triple(13, "GoalD", 20.0),
                    Triple(14, "GoalE", 10.0),
                    Triple(15, "GoalF", 5.0),
                )
            // Force all rounds to be points by using a random that always returns false
            val alwaysPointsRandom =
                object : Random() {
                    private val delegate = Random(seed)
                    private var boolCallCount = 0

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = false // always points
                }
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(alwaysPointsRandom)
            advanceUntilIdle()

            // Collect player IDs across multiple rounds (at least 2 to trigger a reset)
            val allSeenIds = mutableSetOf<Int>()
            allSeenIds.addAll(viewModel.choices.map { it.id })
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()
            allSeenIds.addAll(viewModel.choices.map { it.id })

            // Assert - both tied players (2 and 3) should eventually appear
            assertTrue(
                "Both players with tied values should appear across rounds, but saw: $allSeenIds",
                allSeenIds.contains(2) && allSeenIds.contains(3),
            )
        }

    @Test
    fun `correct answer is highest value among the three choices`() =
        runTest(testDispatcher) {
            // Arrange
            val seed = generateSequence(0) { it + 1 }.first { !Random(it).nextBoolean() }
            val pointsPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 60.0),
                    Triple(4, "Dave", 40.0),
                    Triple(5, "Eve", 20.0),
                    Triple(6, "Frank", 10.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(10, "GoalA", 50.0),
                    Triple(11, "GoalB", 40.0),
                    Triple(12, "GoalC", 30.0),
                    Triple(13, "GoalD", 20.0),
                    Triple(14, "GoalE", 10.0),
                    Triple(15, "GoalF", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Assert
            val maxValue = viewModel.choices.maxOf { it.value }
            assertEquals(
                "Correct player should have the highest value among choices",
                maxValue,
                viewModel.correctPlayer!!.value,
                0.001,
            )
        }

    // =======================================================================
    // Independent Used-Player Tracking
    // =======================================================================

    @Test
    fun `player used in points question CAN appear in goals question`() =
        runTest(testDispatcher) {
            // Arrange - shared players appear in both leaderboards.
            // Points first, then goals.
            val boolSequence = listOf(false, true).iterator()
            val customRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = boolSequence.next()
                }
            // Players 1, 2, 3 appear in both points and goals leaderboards.
            // With 6 players per type, pool = 3. The top 3 by value in each type
            // are shared players.
            val pointsPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 60.0),
                    Triple(4, "Dave", 40.0),
                    Triple(5, "Eve", 20.0),
                    Triple(6, "Frank", 10.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(1, "Alice", 50.0),
                    Triple(2, "Bob", 40.0),
                    Triple(3, "Carol", 30.0),
                    Triple(7, "Grace", 20.0),
                    Triple(8, "Hank", 10.0),
                    Triple(9, "Ivy", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(customRandom)
            advanceUntilIdle()

            // Capture points round choice IDs
            val pointsChoiceIds = viewModel.choices.map { it.id }.toSet()

            // Act - advance to goals round
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Assert - goals round CAN reuse players from points round (cross-type allowed)
            val goalsChoiceIds = viewModel.choices.map { it.id }.toSet()
            val overlap = pointsChoiceIds.intersect(goalsChoiceIds)
            assertTrue(
                "Cross-type reuse should be allowed: players from points ($pointsChoiceIds) should " +
                    "appear in goals ($goalsChoiceIds) since pools overlap, but overlap was: $overlap",
                overlap.isNotEmpty(),
            )
        }

    @Test
    fun `player used in goals question CANNOT appear in another goals question until reset`() =
        runTest(testDispatcher) {
            // Arrange - force two consecutive goals rounds.
            // Goals pool has 6 players -> pool of 3. After using 3 in round 1,
            // round 2 cannot form 3 distinct-value choices -> triggers goals-only reset.
            // But if the pool had 8 -> pool of 4, round 1 uses 3, round 2 has 1 left -> reset.
            // Use 12 goals players -> pool of 6. Round 1 uses 3, round 2 should pick from
            // the remaining 3 (no overlap with round 1).
            val alwaysGoalsRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = true // always goals
                }
            val pointsPlayers =
                listOf(
                    Triple(50, "PtsA", 100.0),
                    Triple(51, "PtsB", 90.0),
                    Triple(52, "PtsC", 80.0),
                    Triple(53, "PtsD", 70.0),
                    Triple(54, "PtsE", 60.0),
                    Triple(55, "PtsF", 50.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(1, "GoalA", 60.0),
                    Triple(2, "GoalB", 55.0),
                    Triple(3, "GoalC", 50.0),
                    Triple(4, "GoalD", 45.0),
                    Triple(5, "GoalE", 40.0),
                    Triple(6, "GoalF", 35.0),
                    Triple(7, "GoalG", 30.0),
                    Triple(8, "GoalH", 25.0),
                    Triple(9, "GoalI", 20.0),
                    Triple(10, "GoalJ", 15.0),
                    Triple(11, "GoalK", 10.0),
                    Triple(12, "GoalL", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(alwaysGoalsRandom)
            advanceUntilIdle()

            // Round 1 (goals)
            val round1Ids = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, round1Ids.size)

            // Act - round 2 (also goals)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Assert - no overlap within same type before reset
            val round2Ids = viewModel.choices.map { it.id }.toSet()
            val overlap = round1Ids.intersect(round2Ids)
            assertTrue(
                "Same-type reuse is forbidden until reset. Round 1: $round1Ids, Round 2: $round2Ids, overlap: $overlap",
                overlap.isEmpty(),
            )
        }

    @Test
    fun `per-type used sets are independent pointsUsedIds and goalsUsedIds`() =
        runTest(testDispatcher) {
            // Arrange - points first, then goals
            val boolSequence = listOf(false, true).iterator()
            val customRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = boolSequence.next()
                }
            val pointsPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 60.0),
                    Triple(4, "Dave", 40.0),
                    Triple(5, "Eve", 20.0),
                    Triple(6, "Frank", 10.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(10, "GoalA", 50.0),
                    Triple(11, "GoalB", 40.0),
                    Triple(12, "GoalC", 30.0),
                    Triple(13, "GoalD", 20.0),
                    Triple(14, "GoalE", 10.0),
                    Triple(15, "GoalF", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(customRandom)
            advanceUntilIdle()

            // After round 1 (points): pointsUsedIds should have 3 IDs, goalsUsedIds should be empty
            assertEquals(
                "After points round, pointsUsedIds should have 3 entries",
                3,
                viewModel.pointsUsedIds.size,
            )
            assertEquals(
                "After points round, goalsUsedIds should be empty",
                0,
                viewModel.goalsUsedIds.size,
            )

            // Act - round 2 (goals)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Assert - both used sets should be independent
            assertEquals(
                "After goals round, pointsUsedIds should still have 3 entries",
                3,
                viewModel.pointsUsedIds.size,
            )
            assertEquals(
                "After goals round, goalsUsedIds should have 3 entries",
                3,
                viewModel.goalsUsedIds.size,
            )
        }

    // =======================================================================
    // Per-Type Reset
    // =======================================================================

    @Test
    fun `goals pool resets independently when exhausted without resetting points used set`() =
        runTest(testDispatcher) {
            // Arrange - goals pool has exactly 3 players (6 total -> pool of 3).
            // After round 1 uses all 3 goals pool entries, round 2 goals should trigger
            // a goals-only reset. Points used set should NOT be affected.
            // Sequence: points, goals, goals
            val boolSequence = listOf(false, true, true).iterator()
            val customRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = boolSequence.next()
                }
            val pointsPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 60.0),
                    Triple(4, "Dave", 40.0),
                    Triple(5, "Eve", 20.0),
                    Triple(6, "Frank", 10.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(10, "GoalA", 50.0),
                    Triple(11, "GoalB", 40.0),
                    Triple(12, "GoalC", 30.0),
                    Triple(13, "GoalD", 20.0),
                    Triple(14, "GoalE", 10.0),
                    Triple(15, "GoalF", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(customRandom)
            advanceUntilIdle()

            // Round 1 (points) - uses 3 from points pool
            val pointsRound1Ids = viewModel.pointsUsedIds.toSet()
            assertEquals(3, pointsRound1Ids.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 2 (goals) - uses 3 from goals pool (all of it)
            assertEquals(3, viewModel.goalsUsedIds.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 3 (goals again) - goals pool is exhausted, should reset goals only
            // Points used set should still contain the original 3 IDs
            assertTrue(
                "Points used set should NOT be reset when goals pool resets. " +
                    "Expected pointsUsedIds to still contain $pointsRound1Ids, " +
                    "but got: ${viewModel.pointsUsedIds}",
                viewModel.pointsUsedIds.containsAll(pointsRound1Ids),
            )
            assertEquals(3, viewModel.choices.size)
        }

    @Test
    fun `points pool resets independently when exhausted without resetting goals used set`() =
        runTest(testDispatcher) {
            // Arrange - points pool has 3 entries (6 total -> pool of 3).
            // Sequence: goals, points, points
            val boolSequence = listOf(true, false, false).iterator()
            val customRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = boolSequence.next()
                }
            val pointsPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 60.0),
                    Triple(4, "Dave", 40.0),
                    Triple(5, "Eve", 20.0),
                    Triple(6, "Frank", 10.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(10, "GoalA", 50.0),
                    Triple(11, "GoalB", 40.0),
                    Triple(12, "GoalC", 30.0),
                    Triple(13, "GoalD", 20.0),
                    Triple(14, "GoalE", 10.0),
                    Triple(15, "GoalF", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(customRandom)
            advanceUntilIdle()

            // Round 1 (goals) - uses 3 from goals pool
            val goalsRound1Ids = viewModel.goalsUsedIds.toSet()
            assertEquals(3, goalsRound1Ids.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 2 (points) - uses 3 from points pool (all of it)
            assertEquals(3, viewModel.pointsUsedIds.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 3 (points again) - points pool exhausted, should reset points only
            // Goals used set should still contain the original 3 IDs
            assertTrue(
                "Goals used set should NOT be reset when points pool resets. " +
                    "Expected goalsUsedIds to still contain $goalsRound1Ids, " +
                    "but got: ${viewModel.goalsUsedIds}",
                viewModel.goalsUsedIds.containsAll(goalsRound1Ids),
            )
            assertEquals(3, viewModel.choices.size)
        }

    @Test
    fun `after per-type reset previously seen players may reappear in that type`() =
        runTest(testDispatcher) {
            // Arrange - goals pool of 3 (6 players -> pool of 3). Force two consecutive
            // goals rounds. Round 2 must reuse players from round 1 after reset.
            val alwaysGoalsRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = true // always goals
                }
            val pointsPlayers =
                listOf(
                    Triple(50, "PtsA", 100.0),
                    Triple(51, "PtsB", 80.0),
                    Triple(52, "PtsC", 60.0),
                    Triple(53, "PtsD", 40.0),
                    Triple(54, "PtsE", 20.0),
                    Triple(55, "PtsF", 10.0),
                )
            val goalsPlayers =
                listOf(
                    Triple(1, "GoalA", 50.0),
                    Triple(2, "GoalB", 40.0),
                    Triple(3, "GoalC", 30.0),
                    Triple(4, "GoalD", 20.0),
                    Triple(5, "GoalE", 10.0),
                    Triple(6, "GoalF", 5.0),
                )
            val json = createStatsJsonWithGoals(pointsPlayers, goalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(alwaysGoalsRandom)
            advanceUntilIdle()

            // Round 1 (goals) - uses all 3 goals pool entries
            val round1Ids = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, round1Ids.size)

            // Act - round 2 (goals again) - pool exhausted, reset, then pick from full pool
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            val round2Ids = viewModel.choices.map { it.id }.toSet()

            // Assert - after reset, round 2 must contain players from round 1
            // (since there are only 3 players in the pool, all 3 must reappear)
            val overlap = round1Ids.intersect(round2Ids)
            assertTrue(
                "After per-type reset, previously seen players should reappear. " +
                    "Round 1: $round1Ids, Round 2: $round2Ids, overlap: $overlap",
                overlap.isNotEmpty(),
            )
        }
}
