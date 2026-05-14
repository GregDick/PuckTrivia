package com.example.pucktrivia

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.pucktrivia.data.DataStoreHighScoreRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class HighScoreRepositoryTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    // Unconfined so DataStore's internal coroutines run eagerly without manual advancing.
    private val testDispatcher = UnconfinedTestDispatcher()

    private val historyKey = stringPreferencesKey("high_score_history")

    private fun TestScope.newDataStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tmpFolder.newFile("$name.preferences_pb") },
        )

    @Test
    fun `submit then read round-trips the score`() =
        runTest(testDispatcher) {
            val repository = DataStoreHighScoreRepository(newDataStore("roundtrip"), testDispatcher)

            repository.submit(score = 250, endedAt = 1_715_000_000_000L)

            val top = repository.topThree()
            assertEquals(1, top.size)
            assertEquals(250, top.first().score)
            assertEquals(1_715_000_000_000L, top.first().endedAt)
        }

    @Test
    fun `topThree on a fresh store is empty`() =
        runTest(testDispatcher) {
            val repository = DataStoreHighScoreRepository(newDataStore("empty"), testDispatcher)
            assertTrue(repository.topThree().isEmpty())
        }

    @Test
    fun `submit reports placement and returns the updated leaderboard`() =
        runTest(testDispatcher) {
            val repository = DataStoreHighScoreRepository(newDataStore("placement"), testDispatcher)

            assertTrue(repository.submit(score = 100, endedAt = 1L).placedInTopThree)
            repository.submit(score = 300, endedAt = 2L)
            repository.submit(score = 200, endedAt = 3L)

            val low = repository.submit(score = 50, endedAt = 4L)
            assertFalse(low.placedInTopThree)
            assertEquals(listOf(300, 200, 100), low.topThree.map { it.score })
        }

    @Test
    fun `full history is retained even past the top three`() =
        runTest(testDispatcher) {
            val dataStore = newDataStore("history")
            val repository = DataStoreHighScoreRepository(dataStore, testDispatcher)

            repeat(5) { repository.submit(score = (it + 1) * 100, endedAt = it.toLong()) }

            // The top-three view is capped...
            assertEquals(3, repository.topThree().size)
            // ...but the underlying history kept all five entries.
            val raw = dataStore.data.first()[historyKey]
            assertEquals(5, raw!!.split(";").drop(1).size)
        }

    @Test
    fun `a corrupt stored value is treated as empty history`() =
        runTest(testDispatcher) {
            val dataStore = newDataStore("corrupt")
            dataStore.edit { it[historyKey] = "not-valid-data" }
            val repository = DataStoreHighScoreRepository(dataStore, testDispatcher)

            assertTrue(repository.topThree().isEmpty())

            // A subsequent submit still works and starts a clean history.
            val result = repository.submit(score = 70, endedAt = 9L)
            assertTrue(result.placedInTopThree)
            assertEquals(listOf(70), result.topThree.map { it.score })
        }

    @Test
    fun `concurrent submits all persist without loss`() =
        runTest(testDispatcher) {
            val dataStore = newDataStore("concurrent")
            val repository = DataStoreHighScoreRepository(dataStore, testDispatcher)

            repeat(10) { i ->
                launch { repository.submit(score = (i + 1) * 10, endedAt = i.toLong()) }
            }
            advanceUntilIdle()

            val raw = dataStore.data.first()[historyKey]
            assertEquals(10, raw!!.split(";").drop(1).size)
        }
}
