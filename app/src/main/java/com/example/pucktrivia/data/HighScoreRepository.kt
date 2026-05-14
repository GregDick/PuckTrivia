package com.example.pucktrivia.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.pucktrivia.di.IoDispatcher
import com.example.pucktrivia.model.HighScore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Outcome of recording a completed game's score. */
data class SubmitResult(
    /** Whether the just-submitted score placed on the leaderboard. */
    val placedInTopThree: Boolean,
    /** The leaderboard after the submission, highest first, at most [TOP_SCORE_COUNT] entries. */
    val topThree: List<HighScore>,
)

/**
 * Stores every completed game's score on the device and exposes the derived top-three leaderboard
 * view. The full history is retained — it is never capped or pruned.
 */
interface HighScoreRepository {

    /** The leaderboard derived from the full persisted history, highest first. */
    suspend fun topThree(): List<HighScore>

    /**
     * Appends a completed game to the history and returns the resulting leaderboard plus whether
     * this score placed on it. A storage failure propagates as an exception; the caller is expected
     * to treat saving as best-effort.
     */
    suspend fun submit(score: Int, endedAt: Long): SubmitResult
}

class DataStoreHighScoreRepository
@Inject
constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HighScoreRepository {

    override suspend fun topThree(): List<HighScore> =
        withContext(ioDispatcher) { HighScoreRanking.topThree(readHistory()) }

    override suspend fun submit(score: Int, endedAt: Long): SubmitResult =
        withContext(ioDispatcher) {
            val entry = HighScore(score = score, endedAt = endedAt)
            var placed = false
            // edit() runs serially per DataStore, so two rapid submits cannot interleave
            // and drop an entry. Placement is computed against the history as it stood
            // before this entry was appended.
            val updatedPrefs = dataStore.edit { prefs ->
                val priorHistory = HighScoreCodec.decode(prefs[HISTORY_KEY])
                placed = HighScoreRanking.placesInTopThree(priorHistory, entry)
                prefs[HISTORY_KEY] = HighScoreCodec.encode(priorHistory + entry)
            }
            SubmitResult(
                placedInTopThree = placed,
                topThree =
                    HighScoreRanking.topThree(HighScoreCodec.decode(updatedPrefs[HISTORY_KEY])),
            )
        }

    private suspend fun readHistory(): List<HighScore> =
        try {
            HighScoreCodec.decode(dataStore.data.first()[HISTORY_KEY])
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read high-score history; treating as empty", e)
            emptyList()
        }

    companion object {
        private const val TAG = "HighScoreRepository"
        private val HISTORY_KEY = stringPreferencesKey("high_score_history")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HighScoreModule {
    @Binds
    abstract fun bindHighScoreRepository(impl: DataStoreHighScoreRepository): HighScoreRepository
}
