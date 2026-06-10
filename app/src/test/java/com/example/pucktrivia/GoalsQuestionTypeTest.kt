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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for question type selection and per-type pool mechanics.
 *
 * Test data uses all-forward ("C") players unless otherwise noted, so only FORWARDS_POINTS and
 * FORWARDS_GOALS pools exist when both stat keys are present.
 *
 * Type selection uses random.nextInt(availableTypes.size). When two types are available (size=2)
 * and each pool has 3 players, shuffle calls are nextInt(3) then nextInt(2). The
 * [makeTypeControlledRandom] helper intercepts the first nextInt call each round (type selection)
 * and delegates shuffle calls to a real Random.
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

    /** Creates JSON with both "points" and "goals" keys. All players are forwards ("C"). */
    private fun createStatsJsonWithGoals(
        pointsPlayers: List<Triple<Int, String, Double>>,
        goalsPlayers: List<Triple<Int, String, Double>>,
    ): String {
        fun playersToJson(players: List<Triple<Int, String, Double>>): String =
            players.joinToString(",") { (id, name, value) ->
                """{"id":$id,"firstName":{"default":"$name"},"lastName":{"default":"Player"},"sweaterNumber":${id + 10},"teamAbbrev":"TST","position":"C","value":$value}"""
            }
        return """{"points":[${playersToJson(pointsPlayers)}],"goals":[${playersToJson(goalsPlayers)}]}"""
    }

    /** Creates JSON with only "points" key (no goals data). All players are forwards ("C"). */
    private fun createPointsOnlyJson(players: List<Triple<Int, String, Double>>): String {
        val playersJson =
            players.joinToString(",") { (id, name, value) ->
                """{"id":$id,"firstName":{"default":"$name"},"lastName":{"default":"Player"},"sweaterNumber":${id + 10},"teamAbbrev":"TST","position":"C","value":$value}"""
            }
        return """{"points":[$playersJson]}"""
    }

    /** Creates JSON with only "goals" key (no points data). All players are forwards ("C"). */
    private fun createGoalsOnlyJson(players: List<Triple<Int, String, Double>>): String {
        val playersJson =
            players.joinToString(",") { (id, name, value) ->
                """{"id":$id,"firstName":{"default":"$name"},"lastName":{"default":"Player"},"sweaterNumber":${id + 10},"teamAbbrev":"TST","position":"C","value":$value}"""
            }
        return """{"goals":[$playersJson]}"""
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
     * Returns a Random that controls type selection for multi-round tests.
     *
     * When two types are available (FORWARDS_POINTS index=0, FORWARDS_GOALS index=1) and each pool
     * has 3 players, the call pattern per round is: nextInt(2) [type selection], nextInt(3)
     * [shuffle], nextInt(2) [shuffle]
     *
     * This helper intercepts the first nextInt call per round as the type selection and delegates
     * the remaining two (shuffle) to the real delegate Random.
     *
     * Assumption: pool size is exactly 3 and no mid-round reset occurs.
     */
    private fun makeTypeControlledRandom(typeIndices: List<Int>): Random {
        val delegate = Random(42)
        val it = typeIndices.iterator()
        var isTypeCall = true
        var shuffleCallsRemaining = 0
        return object : Random() {
            override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

            override fun nextInt(until: Int): Int {
                if (isTypeCall && it.hasNext()) {
                    isTypeCall = false
                    shuffleCallsRemaining = 2 // 2 shuffle calls for pool of size 3
                    return it.next()
                }
                if (shuffleCallsRemaining > 0) {
                    shuffleCallsRemaining--
                    if (shuffleCallsRemaining == 0) isTypeCall = true
                }
                return delegate.nextInt(until)
            }
        }
    }

    private val defaultPointsPlayers =
        listOf(
            Triple(1, "Alice", 100.0),
            Triple(2, "Bob", 80.0),
            Triple(3, "Carol", 60.0),
            Triple(7, "Grace", 40.0),
            Triple(8, "Hank", 20.0),
            Triple(9, "Ivy", 10.0),
        )

    private val defaultGoalsPlayers =
        listOf(
            Triple(4, "Dave", 50.0),
            Triple(5, "Eve", 40.0),
            Triple(6, "Frank", 30.0),
            Triple(10, "Jack", 20.0),
            Triple(11, "Kate", 10.0),
            Triple(12, "Leo", 5.0),
        )

    // -----------------------------------------------------------------------
    // Story 1: Fetch and Store Goals Data
    // -----------------------------------------------------------------------

    @Test
    fun `goals data is stored in statsData under goals key`() =
        runTest(testDispatcher) {
            val json = createStatsJsonWithGoals(defaultPointsPlayers, defaultGoalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertNotNull("statsData should contain 'goals' key", viewModel.statsData["goals"])
            assertEquals(6, viewModel.statsData["goals"]!!.size)
        }

    @Test
    fun `goals entries have correct fields`() =
        runTest(testDispatcher) {
            val json = createStatsJsonWithGoals(defaultPointsPlayers, defaultGoalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

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
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(3, viewModel.choices.size)
            assertNotNull(viewModel.correctPlayer)
        }

    // -----------------------------------------------------------------------
    // Story 2: Randomly Select Question Type Each Round
    // -----------------------------------------------------------------------

    @Test
    fun `viewModel accepts Random constructor parameter`() =
        runTest(testDispatcher) {
            val json = createStatsJsonWithGoals(defaultPointsPlayers, defaultGoalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(0))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertNotNull(viewModel)
        }

    @Test
    fun `points only data always presents forwards points question`() =
        runTest(testDispatcher) {
            // With no goals key, only FORWARDS_POINTS pool exists → always selected
            val json = createPointsOnlyJson(defaultPointsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(
                "Which of these forwards currently has the most points?",
                viewModel.questionText,
            )
            assertEquals("pts", viewModel.statUnitLabel)
        }

    @Test
    fun `goals only data always presents forwards goals question`() =
        runTest(testDispatcher) {
            // With no points key, only FORWARDS_GOALS pool exists → always selected
            val json = createGoalsOnlyJson(defaultGoalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(
                "Which of these forwards currently has the most goals?",
                viewModel.questionText,
            )
            assertEquals("g", viewModel.statUnitLabel)
        }

    @Test
    fun `points question draws choices from forwards points pool`() =
        runTest(testDispatcher) {
            // Points only → FORWARDS_POINTS always selected
            val json = createPointsOnlyJson(defaultPointsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val poolIds = viewModel.pools[QuestionType.FORWARDS_POINTS]!!.map { it.id }.toSet()
            assertTrue(
                "Choices should be drawn from forwards points pool, but got IDs: ${viewModel.choices.map { it.id }}",
                viewModel.choices.all { it.id in poolIds },
            )
        }

    @Test
    fun `goals question draws choices from forwards goals pool`() =
        runTest(testDispatcher) {
            // Goals only → FORWARDS_GOALS always selected
            val json = createGoalsOnlyJson(defaultGoalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val poolIds = viewModel.pools[QuestionType.FORWARDS_GOALS]!!.map { it.id }.toSet()
            assertTrue(
                "Choices should be drawn from forwards goals pool, but got IDs: ${viewModel.choices.map { it.id }}",
                viewModel.choices.all { it.id in poolIds },
            )
        }

    @Test
    fun `correct answer is highest value among goals choices`() =
        runTest(testDispatcher) {
            val json = createGoalsOnlyJson(defaultGoalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val maxValue = viewModel.choices.maxOf { it.value }
            assertEquals(maxValue, viewModel.correctPlayer!!.value, 0.001)
        }

    @Test
    fun `goals data unavailable always presents forwards points question`() =
        runTest(testDispatcher) {
            val json = createPointsOnlyJson(defaultPointsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(
                "Which of these forwards currently has the most points?",
                viewModel.questionText,
            )
            assertEquals("pts", viewModel.statUnitLabel)
        }

    @Test
    fun `statUnitLabel is consistent with questionText on each round`() =
        runTest(testDispatcher) {
            // Both types available — verify label matches question regardless of which is selected
            val json = createStatsJsonWithGoals(defaultPointsPlayers, defaultGoalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            repeat(4) {
                val expectedLabel = if ("points" in viewModel.questionText) "pts" else "g"
                assertEquals(
                    "statUnitLabel must match questionText, but got label=${viewModel.statUnitLabel} for question='${viewModel.questionText}'",
                    expectedLabel,
                    viewModel.statUnitLabel,
                )
                viewModel.selectAnswer(viewModel.correctPlayer!!.id)
                viewModel.nextRound()
            }
        }

    // -----------------------------------------------------------------------
    // No-tie invariant for goals questions
    // -----------------------------------------------------------------------

    @Test
    fun `goals choices have distinct values when goals data has duplicates`() =
        runTest(testDispatcher) {
            // Goals only → FORWARDS_GOALS always selected
            val json =
                createGoalsOnlyJson(
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
                    )
                )
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val values = viewModel.choices.map { it.value }
            assertEquals(
                "All goals choice values must be unique, but got: $values",
                values.size,
                values.distinct().size,
            )
        }

    // -----------------------------------------------------------------------
    // Independent Per-Type Used-Player Pools
    // -----------------------------------------------------------------------

    @Test
    fun `player from forwards points question CAN appear in forwards goals question`() =
        runTest(testDispatcher) {
            // Players 1-3 appear in both leaderboards. Round 1=points, round 2=goals.
            val sharedPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 60.0),
                    Triple(7, "Grace", 40.0),
                    Triple(8, "Hank", 20.0),
                    Triple(9, "Ivy", 10.0),
                )
            val json = createStatsJsonWithGoals(sharedPlayers, sharedPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Round 1 = FORWARDS_POINTS (index 0), Round 2 = FORWARDS_GOALS (index 1)
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(0, 1)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val pointsChoiceIds = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, pointsChoiceIds.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Goals round should succeed — cross-type reuse is allowed
            val goalsChoiceIds = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, goalsChoiceIds.size)
        }

    @Test
    fun `player from forwards goals question CAN appear in forwards points question`() =
        runTest(testDispatcher) {
            val sharedPlayers =
                listOf(
                    Triple(1, "Alice", 100.0),
                    Triple(2, "Bob", 80.0),
                    Triple(3, "Carol", 60.0),
                    Triple(7, "Grace", 40.0),
                    Triple(8, "Hank", 20.0),
                    Triple(9, "Ivy", 10.0),
                )
            val json = createStatsJsonWithGoals(sharedPlayers, sharedPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Round 1 = FORWARDS_GOALS (index 1), Round 2 = FORWARDS_POINTS (index 0)
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(1, 0)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val goalsChoiceIds = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, goalsChoiceIds.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            val pointsChoiceIds = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, pointsChoiceIds.size)
        }

    @Test
    fun `per-type used sets track players independently`() =
        runTest(testDispatcher) {
            val json = createStatsJsonWithGoals(defaultPointsPlayers, defaultGoalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            // Round 1 = FORWARDS_POINTS (0), Round 2 = FORWARDS_GOALS (1)
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(0, 1)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            // After round 1 (points): FORWARDS_POINTS used set has 3, FORWARDS_GOALS is empty
            assertEquals(3, (viewModel.usedIds[QuestionType.FORWARDS_POINTS] ?: emptySet()).size)
            assertEquals(0, (viewModel.usedIds[QuestionType.FORWARDS_GOALS] ?: emptySet()).size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // After round 2 (goals): both used sets are independent
            assertEquals(3, (viewModel.usedIds[QuestionType.FORWARDS_POINTS] ?: emptySet()).size)
            assertEquals(3, (viewModel.usedIds[QuestionType.FORWARDS_GOALS] ?: emptySet()).size)
        }

    @Test
    fun `per-type pool reset when type is exhausted`() =
        runTest(testDispatcher) {
            // Pool of 3 per type. Round 1=points, round 2=goals, round 3=points (exhausted →
            // reset).
            val json = createStatsJsonWithGoals(defaultPointsPlayers, defaultGoalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(makeTypeControlledRandom(listOf(0, 1, 0)))
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            // Round 1 (points)
            assertEquals(3, viewModel.choices.size)
            val goalsUsedAfterRound1 = viewModel.usedIds[QuestionType.FORWARDS_GOALS] ?: emptySet()
            assertEquals(0, goalsUsedAfterRound1.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 2 (goals)
            assertEquals(3, viewModel.choices.size)
            val goalsUsedAfterRound2 = viewModel.usedIds[QuestionType.FORWARDS_GOALS] ?: emptySet()
            assertEquals(3, goalsUsedAfterRound2.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            // Round 3 (points again) — pool exhausted, only points resets
            assertEquals(3, viewModel.choices.size)
            // Goals used set should still contain its entries from round 2
            val goalsUsedAfterRound3 = viewModel.usedIds[QuestionType.FORWARDS_GOALS] ?: emptySet()
            assertTrue(
                "Goals used set should NOT be reset when points resets",
                goalsUsedAfterRound3.containsAll(goalsUsedAfterRound2),
            )
        }

    @Test
    fun `goals pool resets independently allowing round to proceed`() =
        runTest(testDispatcher) {
            // Force two consecutive goals rounds. After round 1 exhausts pool (3 players),
            // round 2 should trigger a goals-only reset and still produce 3 choices.
            val json = createGoalsOnlyJson(defaultGoalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42)) // single type, always FORWARDS_GOALS
            viewModel.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            assertEquals(3, viewModel.choices.size)
            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            assertEquals(
                "Per-type reset should allow goals round to proceed",
                3,
                viewModel.choices.size,
            )
        }

    @Test
    fun `after per-type reset previously seen players reappear`() =
        runTest(testDispatcher) {
            val json = createGoalsOnlyJson(defaultGoalsPlayers)
            mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel(Random(42))
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
}
