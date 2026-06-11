package com.example.pucktrivia

import androidx.lifecycle.SavedStateHandle
import com.example.pucktrivia.data.GameSnapshot
import com.example.pucktrivia.data.GameSnapshotSerializer
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that verify [TriviaViewModel] correctly saves game state to [SavedStateHandle] and restores
 * it on construction — simulating OS-initiated process death and relaunch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TriviaViewModelSavedStateTest {

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

    private fun createDefaultStatsJson(): String {
        val forwards =
            listOf(
                Triple(1, "Alice", 100.0),
                Triple(2, "Bob", 80.0),
                Triple(3, "Carol", 60.0),
                Triple(4, "Dave", 40.0),
                Triple(5, "Eve", 20.0),
                Triple(6, "Frank", 10.0),
            )
        val defenders =
            listOf(
                Triple(11, "Greg", 90.0),
                Triple(12, "Hana", 70.0),
                Triple(13, "Ivan", 50.0),
                Triple(14, "Jess", 30.0),
                Triple(15, "Karl", 15.0),
                Triple(16, "Lena", 5.0),
            )

        fun toJson(id: Int, name: String, value: Double, position: String) =
            """{"id":$id,"firstName":{"default":"$name"},"lastName":{"default":"Player"},""" +
                """"sweaterNumber":${id + 10},"teamAbbrev":"TST","position":"$position","value":$value}"""

        val playersJson =
            (forwards.map { (id, name, v) -> toJson(id, name, v, "C") } +
                    defenders.map { (id, name, v) -> toJson(id, name, v, "D") })
                .joinToString(",")
        return """{"points":[$playersJson]}"""
    }

    private fun enqueueDefaultResponse() {
        mockWebServer.enqueue(MockResponse().setBody(createDefaultStatsJson()).setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
    }

    private fun fakeProvider(): StatsUrlProvider {
        val skaterUrl = mockWebServer.url("/skater").toString()
        val goalieUrl = mockWebServer.url("/goalie").toString()
        return object : StatsUrlProvider {
            override fun skaterUrl(mode: SeasonMode) = skaterUrl

            override fun goalieUrl(mode: SeasonMode) = goalieUrl
        }
    }

    private fun createViewModel(handle: SavedStateHandle = SavedStateHandle()): TriviaViewModel =
        TriviaViewModel(
            client = OkHttpClient(),
            urlProvider = fakeProvider(),
            highScoreRepository = FakeHighScoreRepository(),
            timeProvider = FixedTimeProvider(),
            ioDispatcher = testDispatcher,
            savedStateHandle = handle,
        )

    /**
     * Decodes the snapshot the ViewModel stored in [handle]. The handle holds a [ByteArray] (not
     * the [GameSnapshot] directly — see [GameSnapshotSerializer]'s KDoc), so tests must deserialise
     * it the same way the restore path does.
     */
    private fun readSnapshot(handle: SavedStateHandle): GameSnapshot? =
        GameSnapshotSerializer.fromBytes(handle.get<ByteArray>(TriviaViewModel.KEY_GAME_SNAPSHOT))

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `snapshot is written to handle after prepareRound succeeds`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val handle = SavedStateHandle()
            val vm = createViewModel(handle)
            vm.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val snapshot = readSnapshot(handle)
            assertNotNull("Snapshot should be written after startGame", snapshot)
            assertEquals(SeasonMode.RegularSeason, snapshot!!.selectedMode)
            assertEquals(3, snapshot.choices.size)
        }

    @Test
    fun `snapshot updates after selectAnswer`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val handle = SavedStateHandle()
            val vm = createViewModel(handle)
            vm.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val correctId = vm.correctPlayer!!.id
            vm.selectAnswer(correctId)

            val snapshot = readSnapshot(handle)
            assertNotNull(snapshot)
            assertEquals(correctId, snapshot!!.selectedPlayerId)
            assertEquals(100, snapshot.score)
        }

    @Test
    fun `new ViewModel constructed from saved handle restores score lives round and choices`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val handle = SavedStateHandle()
            val vm1 = createViewModel(handle)
            vm1.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            // Answer correctly on round 1.
            vm1.selectAnswer(vm1.correctPlayer!!.id)
            vm1.nextRound()

            // Answer wrong on round 2 — need new mock responses for nextRound's prepareRound.
            // The pools are still in memory from the first fetch, so no new HTTP is needed.
            val wrongId = vm1.choices.first { it.id != vm1.correctPlayer!!.id }.id
            vm1.selectAnswer(wrongId)

            val expectedScore = vm1.score
            val expectedLives = vm1.lives
            val expectedRound = vm1.roundNumber
            val expectedChoices = vm1.choices
            val expectedSelected = vm1.selectedPlayerId

            // Simulate process death: construct a brand-new ViewModel from the same handle.
            val vm2 = createViewModel(handle)
            // No network calls expected — state restored from snapshot.
            advanceUntilIdle()

            assertEquals("score", expectedScore, vm2.score)
            assertEquals("lives", expectedLives, vm2.lives)
            assertEquals("roundNumber", expectedRound, vm2.roundNumber)
            assertEquals("choices", expectedChoices, vm2.choices)
            assertEquals("selectedPlayerId", expectedSelected, vm2.selectedPlayerId)
            assertNotNull("correctPlayer should be restored", vm2.correctPlayer)
            assertEquals(SeasonMode.RegularSeason, vm2.selectedMode)
        }

    @Test
    fun `restored ViewModel shows answered state with correct isCorrect flag`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val handle = SavedStateHandle()
            val vm1 = createViewModel(handle)
            vm1.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val correctId = vm1.correctPlayer!!.id
            vm1.selectAnswer(correctId)

            // Restore
            val vm2 = createViewModel(handle)
            advanceUntilIdle()

            assertTrue("answered should be true after restore", vm2.answered)
            assertTrue("isCorrect should be true after restore", vm2.isCorrect)
        }

    @Test
    fun `game-over state is restored correctly`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val handle = SavedStateHandle()
            val vm1 = createViewModel(handle)
            vm1.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            // Use up all lives.
            repeat(3) {
                val wrongId = vm1.choices.first { it.id != vm1.correctPlayer!!.id }.id
                vm1.selectAnswer(wrongId)
                if (!vm1.gameOver) vm1.nextRound()
            }
            advanceUntilIdle()
            assertTrue("should be game over", vm1.gameOver)

            // Restore from handle.
            val vm2 = createViewModel(handle)
            advanceUntilIdle()

            assertTrue("restored VM should be game over", vm2.gameOver)
            assertEquals(vm1.score, vm2.score)
        }

    @Test
    fun `usedIds survive the restore so previously seen players are tracked`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val handle = SavedStateHandle()
            val vm1 = createViewModel(handle)
            vm1.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            val usedBefore = vm1.usedIds

            val vm2 = createViewModel(handle)
            advanceUntilIdle()

            assertEquals("usedIds should survive restore", usedBefore, vm2.usedIds)
        }

    @Test
    fun `resetGame clears the snapshot so restored VM starts fresh`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val handle = SavedStateHandle()
            val vm1 = createViewModel(handle)
            vm1.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            vm1.resetGame()
            advanceUntilIdle()

            val vm2 = createViewModel(handle)
            advanceUntilIdle()

            assertNull("selectedMode should be null after reset + restore", vm2.selectedMode)
            assertFalse("should not be loading", vm2.isLoading)
            assertTrue("choices should be empty", vm2.choices.isEmpty())
        }

    @Test
    fun `unexpected handle entry type starts fresh rather than crashing`() =
        runTest(testDispatcher) {
            // An entry of the wrong type must not crash restore: the safe cast to GameSnapshot
            // yields null and the app starts fresh.
            val handle = SavedStateHandle(mapOf(TriviaViewModel.KEY_GAME_SNAPSHOT to "not-valid"))
            val vm = createViewModel(handle)
            advanceUntilIdle()

            assertNull("selectedMode should be null with an unusable snapshot", vm.selectedMode)
            assertFalse("should not be loading", vm.isLoading)
            assertTrue("choices should be empty", vm.choices.isEmpty())
        }

    @Test
    fun `restored game can continue past Next via dataset re-fetch`() =
        runTest(testDispatcher) {
            enqueueDefaultResponse()
            val handle = SavedStateHandle()
            val vm1 = createViewModel(handle)
            vm1.startGame(SeasonMode.RegularSeason)
            advanceUntilIdle()

            // Answer the current question but do not advance.
            vm1.selectAnswer(vm1.correctPlayer!!.id)

            // Simulate process death: a fresh ViewModel restores from the handle. Its pools are
            // empty (strategy A omits the datasets), so continuing must trigger a re-fetch rather
            // than failing fatally.
            val vm2 = createViewModel(handle)
            advanceUntilIdle()
            assertTrue("pools should be empty immediately after restore", vm2.pools.isEmpty())

            // Enqueue the datasets the re-fetch will request, then advance to the next round.
            enqueueDefaultResponse()
            vm2.nextRound()
            advanceUntilIdle()

            assertFalse("should not hit fatalError after restore", vm2.fatalError)
            assertFalse("should not be loading once re-fetch completes", vm2.isLoading)
            assertTrue("pools should be rebuilt by the re-fetch", vm2.pools.isNotEmpty())
            assertEquals("should present a fresh 3-choice question", 3, vm2.choices.size)
            assertNull("selection should be cleared on the new question", vm2.selectedPlayerId)
            assertNotNull("correctPlayer should be set", vm2.correctPlayer)
        }

    @Test
    fun `cold start with empty handle shows Start screen state`() =
        runTest(testDispatcher) {
            val vm = createViewModel(SavedStateHandle())
            advanceUntilIdle()

            assertNull(vm.selectedMode)
            assertFalse(vm.isLoading)
            assertTrue(vm.choices.isEmpty())
        }
}
