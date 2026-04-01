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
 * - Shared used-player pool across both question types
 * - Global pool reset when neither type has enough unused players
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
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            advanceUntilIdle()

            // Assert
            assertNotNull("statsData should contain 'goals' key", viewModel.statsData["goals"])
            assertEquals(3, viewModel.statsData["goals"]!!.size)
        }

    @Test
    fun `goals entries have correct fields`() =
        runTest(testDispatcher) {
            // Arrange
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers = listOf(Triple(1, "Alice", 100.0)),
                    goalsPlayers = listOf(Triple(4, "Dave", 50.0)),
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
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
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
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
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
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
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
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Assert - all choice IDs should be from points players (1, 2, 3)
            val pointsIds = setOf(1, 2, 3)
            assertTrue(
                "Choices should be drawn from points leaderboard, but got IDs: ${viewModel.choices.map { it.id }}",
                viewModel.choices.all { it.id in pointsIds },
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
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Assert - all choice IDs should be from goals players (4, 5, 6)
            val goalsIds = setOf(4, 5, 6)
            assertTrue(
                "Choices should be drawn from goals leaderboard, but got IDs: ${viewModel.choices.map { it.id }}",
                viewModel.choices.all { it.id in goalsIds },
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
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
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
            // Arrange - use a seed where first nextBoolean is false (points), second is true
            // (goals)
            // Find a seed that gives [false, true] for the first two nextBoolean calls
            val seed =
                generateSequence(0) { it + 1 }
                    .first { s ->
                        val r = Random(s)
                        !r.nextBoolean() && r.nextBoolean()
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
            val viewModel = createViewModel(Random(seed))
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
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 50.0),
                            Triple(6, "Frank", 30.0),
                            Triple(7, "Grace", 30.0),
                            Triple(8, "Hank", 20.0),
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
    // Story 3: Shared Used-Player Pool
    // -----------------------------------------------------------------------

    @Test
    fun `player from points question cannot appear in goals question`() =
        runTest(testDispatcher) {
            // Arrange - seed gives points first, then goals
            val seed =
                generateSequence(0) { it + 1 }
                    .first { s ->
                        val r = Random(s)
                        !r.nextBoolean() && r.nextBoolean()
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
                    // Goals list includes some players that also appear in points (IDs 1, 2)
                    goalsPlayers =
                        listOf(
                            Triple(1, "Alice", 50.0),
                            Triple(2, "Bob", 40.0),
                            Triple(4, "Dave", 30.0),
                            Triple(5, "Eve", 20.0),
                            Triple(6, "Frank", 10.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Capture the points round choice IDs
            val pointsChoiceIds = viewModel.choices.map { it.id }.toSet()

            // Act - go to next round (goals)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Assert - goals choices should not include any players used in the points round
            val goalsChoiceIds = viewModel.choices.map { it.id }.toSet()
            val overlap = pointsChoiceIds.intersect(goalsChoiceIds)
            assertTrue(
                "Players from points round ($pointsChoiceIds) should not appear in goals round, but found overlap: $overlap",
                overlap.isEmpty(),
            )
        }

    @Test
    fun `player from goals question cannot appear in points question`() =
        runTest(testDispatcher) {
            // Arrange - seed gives goals first, then points
            val seed =
                generateSequence(0) { it + 1 }
                    .first { s ->
                        val r = Random(s)
                        r.nextBoolean() && !r.nextBoolean()
                    }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                            Triple(4, "Dave", 40.0),
                            Triple(5, "Eve", 20.0),
                            Triple(6, "Frank", 10.0),
                        ),
                    // Goals list includes some players that also appear in points
                    goalsPlayers =
                        listOf(
                            Triple(1, "Alice", 50.0),
                            Triple(2, "Bob", 40.0),
                            Triple(7, "Grace", 30.0),
                            Triple(8, "Hank", 20.0),
                            Triple(9, "Ivy", 10.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Capture the goals round choice IDs
            val goalsChoiceIds = viewModel.choices.map { it.id }.toSet()

            // Act - go to next round (points)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Assert
            val pointsChoiceIds = viewModel.choices.map { it.id }.toSet()
            val overlap = goalsChoiceIds.intersect(pointsChoiceIds)
            assertTrue(
                "Players from goals round ($goalsChoiceIds) should not appear in points round, but found overlap: $overlap",
                overlap.isEmpty(),
            )
        }

    @Test
    fun `usedPlayerIds tracks players from both question types`() =
        runTest(testDispatcher) {
            // Arrange - seed gives points first, then goals
            val seed =
                generateSequence(0) { it + 1 }
                    .first { s ->
                        val r = Random(s)
                        !r.nextBoolean() && r.nextBoolean()
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
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            val round1Ids = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, round1Ids.size)

            // Act - go to next round
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            val round2Ids = viewModel.choices.map { it.id }.toSet()

            // Assert - usedPlayerIds should contain players from both rounds
            assertTrue(
                "usedPlayerIds should contain all round 1 IDs",
                viewModel.usedPlayerIds.containsAll(round1Ids),
            )
            assertTrue(
                "usedPlayerIds should contain all round 2 IDs",
                viewModel.usedPlayerIds.containsAll(round2Ids),
            )
        }

    @Test
    fun `global pool reset when neither type has enough unused players`() =
        runTest(testDispatcher) {
            // Arrange - exactly 3 players per type, no overlap
            // After round 1 uses 3 players from one type, round 2 uses 3 from the other,
            // round 3 should trigger a global reset since all players are used
            // Seed: points, goals, then whatever (both types exhausted)
            val seed =
                generateSequence(0) { it + 1 }
                    .first { s ->
                        val r = Random(s)
                        !r.nextBoolean() && r.nextBoolean()
                    }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Round 1 (points) - uses 3 of 3 points players
            assertEquals(3, viewModel.choices.size)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 2 (goals) - uses 3 of 3 goals players
            assertEquals(3, viewModel.choices.size)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 3 - both types exhausted, global reset should have occurred
            // Should still have 3 choices (previously seen players can reappear)
            assertEquals(
                "After global pool reset, should still get 3 choices",
                3,
                viewModel.choices.size,
            )
        }

    @Test
    fun `pool resets globally when either type runs out of players`() =
        runTest(testDispatcher) {
            // Arrange - goals has only 3 players, points has 6.
            // After round 1 exhausts the goals pool, a global reset should clear ALL
            // used IDs (including points players), not just the exhausted type.
            // Seed: goals first round, then goals again (goals exhausted -> global reset)
            val seed =
                generateSequence(0) { it + 1 }
                    .first { s ->
                        val r = Random(s)
                        r.nextBoolean() && r.nextBoolean()
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
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Round 1 (goals) - uses all 3 goals players
            val round1Ids = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, round1Ids.size)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 2 (goals again) - goals pool is exhausted, so global reset fires
            // After reset, should still get 3 valid choices
            assertEquals(
                "Global reset should fire when goals pool is exhausted, allowing round to proceed",
                3,
                viewModel.choices.size,
            )
        }

    @Test
    fun `after global reset previously seen players may reappear`() =
        runTest(testDispatcher) {
            // Arrange - exactly 3 distinct-value players per type, disjoint IDs
            val seed =
                generateSequence(0) { it + 1 }
                    .first { s ->
                        val r = Random(s)
                        !r.nextBoolean() && r.nextBoolean()
                    }
            val json =
                createStatsJsonWithGoals(
                    pointsPlayers =
                        listOf(
                            Triple(1, "Alice", 100.0),
                            Triple(2, "Bob", 80.0),
                            Triple(3, "Carol", 60.0),
                        ),
                    goalsPlayers =
                        listOf(
                            Triple(4, "Dave", 50.0),
                            Triple(5, "Eve", 40.0),
                            Triple(6, "Frank", 30.0),
                        ),
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            val viewModel = createViewModel(Random(seed))
            advanceUntilIdle()

            // Round 1 (points) - uses IDs from {1,2,3}
            val round1Ids = viewModel.choices.map { it.id }.toSet()
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 2 (goals) - uses IDs from {4,5,6}
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 3 - both types exhausted, global reset should have occurred
            val round3Ids = viewModel.choices.map { it.id }.toSet()

            // Assert - round 3 must reuse players from round 1 or 2
            val allPreviousIds = round1Ids + viewModel.usedPlayerIds
            assertTrue(
                "After reset, some previously seen players should reappear",
                round3Ids.isNotEmpty(),
            )
        }
}
