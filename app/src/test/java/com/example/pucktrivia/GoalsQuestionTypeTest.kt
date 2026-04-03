package com.example.pucktrivia

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the "most goals" question type feature.
 *
 * These tests verify:
 * - ViewModel accepts a Random constructor parameter
 * - ViewModel exposes statUnitLabel and questionText state
 * - Random selection between "most points" and "most goals" questions
 * - Goals data stored under "goals" key in statsData
 * - Fallback to points-only when goals data is unavailable
 * - Independent per-type used-player pools
 * - Per-type pool reset when a type runs out of unused players
 * - No-tie invariant for goals questions
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalsQuestionTypeTest {

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

    /** Creates a ViewModel with a Random parameter for deterministic question type selection. */
    private fun createViewModel(random: Random): TriviaViewModel {
        return TriviaViewModel(OkHttpClient(), mockUrl(), testDispatcher, random)
    }

    // -----------------------------------------------------------------------
    // Story 1: Fetch and Store Goals Data
    // -----------------------------------------------------------------------

    @Test
    fun `goals data is stored in statsData under goals key`() =
        runTest(testDispatcher) {
            // Arrange
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            advanceUntilIdle()

            // Assert
            assertNotNull("statsData should contain 'goals' key", viewModel.statsData["goals"])
            assertEquals(6, viewModel.statsData["goals"]!!.size)
        }

    @Test
    fun `goals entries have correct fields`() =
        runTest(testDispatcher) {
            // Arrange
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            advanceUntilIdle()

            // Assert
            val goalsPlayer = viewModel.statsData["goals"]!!.first()
            assertEquals(4, goalsPlayer.id)
            assertEquals("Dave", goalsPlayer.firstName)
            assertEquals("Player", goalsPlayer.lastName)
            assertEquals("TST", goalsPlayer.teamAbbrev)
            assertEquals("C", goalsPlayer.position)
            assertEquals(50.0, goalsPlayer.value, 0.001)
        }

    @Test
    fun `missing goals key falls back to points without crash`() =
        runTest(testDispatcher) {
            // Arrange - JSON has only "points", no "goals"
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

            // Assert - should not crash, should have choices from points data
            assertEquals(3, viewModel.choices.size)
            assertNotNull(viewModel.correctPlayer)
        }

    // -----------------------------------------------------------------------
    // Story 2: Randomly Select Question Type Each Round
    // -----------------------------------------------------------------------

    @Test
    fun `viewModel accepts Random constructor parameter`() =
        runTest(testDispatcher) {
            // Arrange & Act - this test verifies the constructor signature compiles
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(0))
            advanceUntilIdle()

            // Assert - ViewModel was created successfully
            assertNotNull(viewModel)
        }

    @Test
    fun `seeded random producing 0 selects points question`() =
        runTest(testDispatcher) {
            // Arrange - use a seeded Random that produces nextBoolean() = false (points)
            // We find a seed that gives nextBoolean() = false
            val seed = generateSequence(0) { it + 1 }.first { !Random(it).nextBoolean() }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Assert
            assertEquals(
                "Which of these players currently has the most points?",
                viewModel.questionText,
            )
            assertEquals("pts", viewModel.statUnitLabel)
        }

    @Test
    fun `seeded random producing 1 selects goals question`() =
        runTest(testDispatcher) {
            // Arrange - use a seeded Random that produces nextBoolean() = true (goals)
            val seed = generateSequence(0) { it + 1 }.first { Random(it).nextBoolean() }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Assert
            assertEquals(
                "Which of these players currently has the most goals?",
                viewModel.questionText,
            )
            assertEquals("g", viewModel.statUnitLabel)
        }

    @Test
    fun `points question draws choices from points leaderboard`() =
        runTest(testDispatcher) {
            // Arrange - force points question via seed
            val seed = generateSequence(0) { it + 1 }.first { !Random(it).nextBoolean() }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Assert - all choice IDs should be from points pool (top 50% = IDs 1, 2, 3)
            val pointsPoolIds = viewModel.pointsPool.map { it.id }.toSet()
            assertTrue(
                "Choices should be drawn from points pool, but got IDs: ${viewModel.choices.map { it.id }}",
                viewModel.choices.all { it.id in pointsPoolIds },
            )
        }

    @Test
    fun `goals question draws choices from goals leaderboard`() =
        runTest(testDispatcher) {
            // Arrange - force goals question via seed
            val seed = generateSequence(0) { it + 1 }.first { Random(it).nextBoolean() }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Assert - all choice IDs should be from goals pool (top 50% = IDs 4, 5, 6)
            val goalsPoolIds = viewModel.goalsPool!!.map { it.id }.toSet()
            assertTrue(
                "Choices should be drawn from goals pool, but got IDs: ${viewModel.choices.map { it.id }}",
                viewModel.choices.all { it.id in goalsPoolIds },
            )
        }

    @Test
    fun `correct answer is highest value among goals choices`() =
        runTest(testDispatcher) {
            // Arrange - force goals question
            val seed = generateSequence(0) { it + 1 }.first { Random(it).nextBoolean() }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Assert
            val maxValue = viewModel.choices.maxOf { it.value }
            assertEquals(maxValue, viewModel.correctPlayer!!.value, 0.001)
        }

    @Test
    fun `goals data unavailable always presents points question`() =
        runTest(testDispatcher) {
            // Arrange - no goals key, seed that would select goals
            val seed = generateSequence(0) { it + 1 }.first { Random(it).nextBoolean() }
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
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Assert - should fall back to points
            assertEquals(
                "Which of these players currently has the most points?",
                viewModel.questionText,
            )
            assertEquals("pts", viewModel.statUnitLabel)
        }

    @Test
    fun `statUnitLabel changes when question type changes across rounds`() =
        runTest(testDispatcher) {
            // Arrange - first round points (false), second round goals (true)
            val boolSequence = listOf(false, true).iterator()
            val customRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = boolSequence.next()
                }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(customRandom)
            advanceUntilIdle()

            // Assert - first round is points
            assertEquals("pts", viewModel.statUnitLabel)

            // Act - go to next round (should be goals)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Assert
            assertEquals("g", viewModel.statUnitLabel)
        }

    // -----------------------------------------------------------------------
    // Story 2 (continued): No-tie invariant for goals questions
    // -----------------------------------------------------------------------

    @Test
    fun `goals choices have distinct values when goals data has duplicates`() =
        runTest(testDispatcher) {
            // Arrange - force goals question, goals data has duplicate values
            val seed = generateSequence(0) { it + 1 }.first { Random(it).nextBoolean() }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(13, "Mia", 40.0),
                            Triple(14, "Nick", 20.0),
                            Triple(15, "Olivia", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 50.0),
                            Triple(6, "Frank", 30.0),
                            Triple(7, "Grace", 30.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Irene", 15.0),
                            Triple(10, "Jim", 10.0),
                            Triple(11, "Kelly", 5.0),
                            Triple(12, "Larry", 3.0),
                            Triple(16, "Pete", 1.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Assert
            val values = viewModel.choices.map { it.value }
            assertEquals(
                "All goals choice values must be unique, but got: $values",
                values.size,
                values.distinct().size,
            )
        }

    // -----------------------------------------------------------------------
    // Story 3: Independent Per-Type Used-Player Pools
    // -----------------------------------------------------------------------

    @Test
    fun `player from points question CAN appear in goals question`() =
        runTest(testDispatcher) {
            // Arrange - points first, then goals
            // Players 1, 2, 3 appear in both leaderboards' top 50%
            val boolSequence = listOf(false, true).iterator()
            val customRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = boolSequence.next()
                }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(1, "Alice", 50.0),
                            Triple(2, "Bob", 40.0),
                            Triple(3, "Carol", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(customRandom)
            advanceUntilIdle()

            // Round 1 (points) - uses players from points pool
            val pointsChoiceIds = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, pointsChoiceIds.size)

            // Act - go to next round (goals)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Assert - goals round should succeed with 3 choices;
            // cross-type reuse is allowed since pools are independent
            val goalsChoiceIds = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, goalsChoiceIds.size)
        }

    @Test
    fun `player from goals question CAN appear in points question`() =
        runTest(testDispatcher) {
            // Arrange - goals first, then points
            val boolSequence = listOf(true, false).iterator()
            val customRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = boolSequence.next()
                }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(1, "Alice", 50.0),
                            Triple(2, "Bob", 40.0),
                            Triple(3, "Carol", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(customRandom)
            advanceUntilIdle()

            // Round 1 (goals)
            val goalsChoiceIds = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, goalsChoiceIds.size)

            // Act - go to next round (points)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Assert - points round should succeed; cross-type reuse is allowed
            val pointsChoiceIds = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, pointsChoiceIds.size)
        }

    @Test
    fun `per-type used sets track players independently`() =
        runTest(testDispatcher) {
            // Arrange - points first, then goals
            val boolSequence = listOf(false, true).iterator()
            val customRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = boolSequence.next()
                }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(customRandom)
            advanceUntilIdle()

            // After round 1 (points): pointsUsedIds has 3, goalsUsedIds is empty
            assertEquals(3, viewModel.pointsUsedIds.size)
            assertEquals(0, viewModel.goalsUsedIds.size)

            // Act - go to next round (goals)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Assert - both used sets are independent
            assertEquals(3, viewModel.pointsUsedIds.size)
            assertEquals(3, viewModel.goalsUsedIds.size)
        }

    @Test
    fun `per-type pool reset when type is exhausted`() =
        runTest(testDispatcher) {
            // Arrange - 6 players per type (pool of 3 each).
            // Sequence: points, goals, then points again. Third round exhausts points, triggers
            // points-only reset. Goals used set should be unaffected.
            // Use a custom Random that returns [false, true, false] for nextBoolean()
            // to control question type selection regardless of shuffled() consuming random values.
            val boolSequence = listOf(false, true, false).iterator()
            val customRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = boolSequence.next()
                }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(customRandom)
            advanceUntilIdle()

            // Round 1 (points) - uses all 3 points pool entries
            assertEquals(3, viewModel.choices.size)
            val goalsUsedAfterRound1 = viewModel.goalsUsedIds.size
            assertEquals(0, goalsUsedAfterRound1)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 2 (goals) - uses 3 goals pool entries
            assertEquals(3, viewModel.choices.size)
            val goalsUsedAfterRound2 = viewModel.goalsUsedIds.toSet()
            assertEquals(3, goalsUsedAfterRound2.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 3 (points again) - points pool exhausted, resets points only
            assertEquals(3, viewModel.choices.size)
            // Goals used set should still contain its 3 entries from round 2
            assertTrue(
                "Goals used set should NOT be reset when points resets",
                viewModel.goalsUsedIds.containsAll(goalsUsedAfterRound2),
            )
        }

    @Test
    fun `goals pool resets independently allowing round to proceed`() =
        runTest(testDispatcher) {
            // Arrange - force two consecutive goals rounds.
            // Goals pool has 3 entries (from 6 players). After round 1 exhausts it,
            // round 2 should trigger a goals-only reset.
            val alwaysGoalsRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = true // always goals
                }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(alwaysGoalsRandom)
            advanceUntilIdle()

            // Round 1 (goals) - uses all 3 goals pool entries
            assertEquals(3, viewModel.choices.size)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 2 (goals again) - goals exhausted, per-type reset fires
            assertEquals(
                "Per-type reset should allow goals round to proceed",
                3,
                viewModel.choices.size,
            )
        }

    @Test
    fun `after per-type reset previously seen players reappear`() =
        runTest(testDispatcher) {
            // Arrange - force two consecutive goals rounds with pool of 3
            val alwaysGoalsRandom =
                object : Random() {
                    private val delegate = Random(42)

                    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

                    override fun nextBoolean(): Boolean = true // always goals
                }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(7, "Grace", 40.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                            Triple(10, "Jack", 20.0),
                            Triple(11, "Kate", 10.0),
                            Triple(12, "Leo", 5.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(alwaysGoalsRandom)
            advanceUntilIdle()

            // Round 1 (goals) - uses all 3 goals pool entries
            val round1Ids = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, round1Ids.size)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 2 (goals) - after reset, must reuse players from round 1
            val round2Ids = viewModel.choices.map { it.id }.toSet()
            val overlap = round1Ids.intersect(round2Ids)
            assertTrue(
                "After per-type reset, previously seen players should reappear. " +
                    "Round 1: $round1Ids, Round 2: $round2Ids",
                overlap.isNotEmpty(),
            )
        }
}
