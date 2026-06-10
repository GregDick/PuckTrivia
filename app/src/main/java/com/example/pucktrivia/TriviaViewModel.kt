package com.example.pucktrivia

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pucktrivia.data.GameSnapshot
import com.example.pucktrivia.data.GameStateCodec
import com.example.pucktrivia.data.HighScoreRepository
import com.example.pucktrivia.data.TimeProvider
import com.example.pucktrivia.di.IoDispatcher
import com.example.pucktrivia.di.StatsUrlProvider
import com.example.pucktrivia.model.GoalieStatLeader
import com.example.pucktrivia.model.HighScore
import com.example.pucktrivia.model.QuestionType
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.model.SkaterStatLeader
import com.example.pucktrivia.model.StatLeader
import com.example.pucktrivia.model.positionGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@HiltViewModel
class TriviaViewModel
@Inject
constructor(
    private val client: OkHttpClient,
    private val urlProvider: StatsUrlProvider,
    private val highScoreRepository: HighScoreRepository,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val random: kotlin.random.Random = kotlin.random.Random,
) : ViewModel() {

    var statsData by mutableStateOf<Map<String, List<SkaterStatLeader>>>(emptyMap())
        private set

    var goalieStatsData by mutableStateOf<Map<String, List<GoalieStatLeader>>>(emptyMap())
        internal set

    var selectedMode by mutableStateOf<SeasonMode?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var loadError by mutableStateOf(false)
        private set

    var score by mutableIntStateOf(0)
        private set

    var roundNumber by mutableIntStateOf(0)
        private set

    var pools by mutableStateOf<Map<QuestionType, List<StatLeader>>>(emptyMap())
        internal set

    var usedIds by mutableStateOf<Map<QuestionType, Set<Int>>>(emptyMap())
        internal set

    var selectedPlayerId by mutableStateOf<Int?>(null)
        private set

    var choices by mutableStateOf<List<StatLeader>>(emptyList())
        private set

    var correctPlayer by mutableStateOf<StatLeader?>(null)
        private set

    var questionText by mutableStateOf("")
        private set

    var statUnitLabel by mutableStateOf("pts")
        private set

    var lives by mutableIntStateOf(3)
        private set

    var totalAnswered by mutableIntStateOf(0)
        private set

    var correctAnswered by mutableIntStateOf(0)
        private set

    var gameOver by mutableStateOf(false)
        private set

    /** Top-three leaderboard, populated after the score for a finished game is saved. */
    var highScores by mutableStateOf<List<HighScore>>(emptyList())
        private set

    /** Whether the just-finished game's score placed on the leaderboard. */
    var placedInTopThree by mutableStateOf(false)
        private set

    /**
     * The just-finished game's entry (score + end time), used to highlight its row in the
     * leaderboard. Null until a game ends and its score is recorded.
     */
    var currentGameHighScore by mutableStateOf<HighScore?>(null)
        private set

    /** Guards against saving the same finished game's score more than once. */
    private var scoreSaved = false

    var fatalError by mutableStateOf(false)
        private set

    var playoffsUnavailable by mutableStateOf(false)
        private set

    /**
     * Top-three leaderboard loaded from durable storage for display on the Start screen. Populated
     * on construction and refreshed after [resetGame] so returning from a finished game shows fresh
     * scores.
     */
    var startScreenHighScores by mutableStateOf<List<HighScore>>(emptyList())
        private set

    val answered: Boolean
        get() = selectedPlayerId != null

    val isCorrect: Boolean
        get() = selectedPlayerId == correctPlayer?.id

    init {
        // Restore in-progress game from a prior process if a valid snapshot exists.
        val snapshot = GameStateCodec.decode(savedStateHandle[KEY_GAME_SNAPSHOT])
        if (snapshot != null && snapshot.choices.isNotEmpty()) {
            applySnapshot(snapshot)
        } else if (snapshot != null) {
            // Snapshot present but no choices — process was killed mid-fetch. Clear and let the
            // user restart from the Start screen rather than showing a broken state.
            savedStateHandle.remove<String>(KEY_GAME_SNAPSHOT)
        }
        // Load the start-screen leaderboard non-blocking; it populates reactively.
        loadStartScreenLeaderboard()
    }

    /** Applies a decoded [GameSnapshot] to all ViewModel state fields synchronously. */
    private fun applySnapshot(snapshot: GameSnapshot) {
        selectedMode = snapshot.selectedMode
        score = snapshot.score
        lives = snapshot.lives
        roundNumber = snapshot.roundNumber
        totalAnswered = snapshot.totalAnswered
        correctAnswered = snapshot.correctAnswered
        gameOver = snapshot.gameOver
        selectedPlayerId = snapshot.selectedPlayerId
        questionText = snapshot.questionText
        statUnitLabel = snapshot.statUnitLabel
        choices = snapshot.choices
        correctPlayer = snapshot.choices.firstOrNull { it.id == snapshot.correctPlayerId }
        usedIds = snapshot.usedIds
        // Prevent a second score-save for an already-finished restored game.
        if (snapshot.gameOver) scoreSaved = true
    }

    /** Writes the current game state to [savedStateHandle] so it survives process death. */
    private fun persistSnapshot() {
        val mode = selectedMode ?: return
        val correct = correctPlayer ?: return
        savedStateHandle[KEY_GAME_SNAPSHOT] =
            GameStateCodec.encode(
                GameSnapshot(
                    selectedMode = mode,
                    score = score,
                    lives = lives,
                    roundNumber = roundNumber,
                    totalAnswered = totalAnswered,
                    correctAnswered = correctAnswered,
                    gameOver = gameOver,
                    selectedPlayerId = selectedPlayerId,
                    questionText = questionText,
                    statUnitLabel = statUnitLabel,
                    correctPlayerId = correct.id,
                    choices = choices,
                    usedIds = usedIds,
                )
            )
    }

    /** Loads the persisted leaderboard from DataStore for the Start screen (non-blocking). */
    private fun loadStartScreenLeaderboard() {
        viewModelScope.launch {
            try {
                startScreenHighScores = highScoreRepository.topThree()
            } catch (e: Exception) {
                Log.e("TriviaViewModel", "Failed to load start-screen leaderboard", e)
            }
        }
    }

    fun startGame(mode: SeasonMode) {
        if (isLoading) return
        selectedMode = mode
        isLoading = true
        // loadError, fatalError, and playoffsUnavailable are mutually exclusive: reset together
        // here and only one is set during a fetch attempt. MainActivity's `when` ordering relies
        // on this invariant.
        loadError = false
        fatalError = false
        playoffsUnavailable = false
        viewModelScope.launch {
            try {
                val skaterData: Map<String, List<SkaterStatLeader>>
                val goalieData: Map<String, List<GoalieStatLeader>>
                coroutineScope {
                    val skaterDeferred = async { fetchSkaterStats(mode) }
                    val goalieDeferred = async { fetchGoalieStats(mode) }
                    skaterData = skaterDeferred.await()
                    goalieData = goalieDeferred.await()
                }
                statsData = skaterData
                goalieStatsData = goalieData
                buildPools(skaterData, goalieData)
                if (pools.isEmpty() && mode == SeasonMode.Playoffs) {
                    playoffsUnavailable = true
                } else {
                    prepareRound()
                }
            } catch (e: Exception) {
                Log.e("TriviaViewModel", "Failed to fetch stats", e)
                loadError = true
            } finally {
                isLoading = false
            }
        }
    }

    fun selectAnswer(playerId: Int) {
        selectedPlayerId = playerId
        totalAnswered++
        if (playerId == correctPlayer?.id) {
            score += 100
            correctAnswered++
        } else {
            lives = maxOf(0, lives - 1)
        }
        persistSnapshot()
    }

    fun nextRound() {
        // Already finished: the score is saved and there is nothing more to advance. The
        // scoreSaved guard would block a re-save anyway, but returning early keeps the
        // post-game-over state fully frozen.
        if (gameOver) return
        roundNumber++
        selectedPlayerId = null
        if (lives == 0) {
            gameOver = true
            saveScore()
            persistSnapshot()
            return
        }
        prepareRound()
    }

    /**
     * Persists the finished game's score exactly once. The timestamp is captured here, at the
     * moment the game ends, not when the save coroutine later runs. A storage failure is logged and
     * swallowed — it must never crash or block the game-over flow.
     */
    private fun saveScore() {
        if (scoreSaved) return
        scoreSaved = true
        val entry = HighScore(score = score, endedAt = timeProvider.nowMillis())
        viewModelScope.launch {
            try {
                val result = highScoreRepository.submit(entry.score, entry.endedAt)
                // Set together so the screen never sees a highlighted current-game entry
                // alongside an empty leaderboard.
                currentGameHighScore = entry
                highScores = result.topThree
                placedInTopThree = result.placedInTopThree
            } catch (e: Exception) {
                Log.e("TriviaViewModel", "Failed to save high score", e)
            }
        }
    }

    fun resetGame() {
        // Clear the saved snapshot so a subsequent process kill does not restore this game.
        savedStateHandle.remove<String>(KEY_GAME_SNAPSHOT)
        selectedMode = null
        isLoading = false
        loadError = false
        fatalError = false
        playoffsUnavailable = false
        gameOver = false
        highScores = emptyList()
        placedInTopThree = false
        currentGameHighScore = null
        scoreSaved = false
        lives = 3
        score = 0
        totalAnswered = 0
        correctAnswered = 0
        selectedPlayerId = null
        usedIds = emptyMap()
        statsData = emptyMap()
        goalieStatsData = emptyMap()
        pools = emptyMap()
        choices = emptyList()
        correctPlayer = null
        questionText = ""
        // Refresh the start-screen leaderboard so a score just finished shows up.
        loadStartScreenLeaderboard()
    }

    private fun buildPools(
        skaterData: Map<String, List<SkaterStatLeader>>,
        goalieData: Map<String, List<GoalieStatLeader>>,
    ) {
        val built = mutableMapOf<QuestionType, List<StatLeader>>()
        for (type in QuestionType.entries) {
            if (type.positionGroup != null) {
                val players = skaterData[type.statKey] ?: continue
                val group = players.filter { it.positionGroup() == type.positionGroup }
                if (group.isEmpty()) continue
                val sorted = group.sortedByDescending { it.value }
                built[type] = sorted.take(kotlin.math.ceil(sorted.size * type.poolFraction).toInt())
            } else {
                val savePctgList = goalieData[type.statKey] ?: continue
                if (savePctgList.isEmpty()) continue
                val sorted = savePctgList.sortedByDescending { it.value }
                built[type] = sorted.take(kotlin.math.ceil(sorted.size * type.poolFraction).toInt())
            }
        }
        pools = built
    }

    private fun prepareRound() {
        if (pools.isEmpty()) {
            Log.e("TriviaViewModel", "No pools available — cannot prepare round")
            fatalError = true
            return
        }

        val types = pools.keys.toList()
        val type = types[random.nextInt(types.size)]
        val pool = pools[type]!!
        var currentUsed = usedIds[type] ?: emptySet()

        var picked = greedyPick(pool, currentUsed)
        if (picked.size < 3) {
            currentUsed = emptySet()
            picked = greedyPick(pool, currentUsed)
        }
        if (picked.size < 3) {
            Log.e(
                "TriviaViewModel",
                "Pool for $type cannot produce 3 distinct choices even after reset " +
                    "(poolSize=${pool.size}, distinctValues=${pool.distinctBy { it.value }.size})",
            )
            fatalError = true
            return
        }

        questionText = type.questionText(selectedMode ?: SeasonMode.RegularSeason)
        statUnitLabel = type.unitLabel
        usedIds = usedIds + (type to (currentUsed + picked.map { it.id }))
        choices = picked
        correctPlayer = picked.maxByOrNull { it.value }
        persistSnapshot()
    }

    private fun greedyPick(pool: List<StatLeader>, usedIds: Set<Int>): List<StatLeader> {
        val unused = pool.filter { it.id !in usedIds }.shuffled(random)
        val claimedValues = mutableSetOf<Double>()
        val result = mutableListOf<StatLeader>()
        for (player in unused) {
            if (player.value !in claimedValues) {
                claimedValues.add(player.value)
                result.add(player)
                if (result.size == 3) break
            }
        }
        return result
    }

    private suspend fun fetchSkaterStats(mode: SeasonMode): Map<String, List<SkaterStatLeader>> =
        withContext(ioDispatcher) {
            val request = Request.Builder().url(urlProvider.skaterUrl(mode)).build()
            val response = client.newCall(request).execute()
            val json =
                JSONObject(response.body?.string() ?: throw IOException("Empty response body"))

            val result = mutableMapOf<String, List<SkaterStatLeader>>()
            for (key in json.keys()) {
                val playersArray = json.getJSONArray(key)
                val players = mutableListOf<SkaterStatLeader>()
                for (i in 0 until playersArray.length()) {
                    val player = playersArray.getJSONObject(i)
                    players.add(
                        SkaterStatLeader(
                            id = player.getInt("id"),
                            firstName = player.getJSONObject("firstName").getString("default"),
                            lastName = player.getJSONObject("lastName").getString("default"),
                            sweaterNumber = player.optInt("sweaterNumber", -1).takeIf { it != -1 },
                            teamAbbrev = player.getString("teamAbbrev"),
                            position = player.getString("position"),
                            value = player.getDouble("value"),
                        )
                    )
                }
                result[key] = players
            }
            result
        }

    private suspend fun fetchGoalieStats(mode: SeasonMode): Map<String, List<GoalieStatLeader>> =
        withContext(ioDispatcher) {
            val request = Request.Builder().url(urlProvider.goalieUrl(mode)).build()
            val response = client.newCall(request).execute()
            val json =
                JSONObject(response.body?.string() ?: throw IOException("Empty response body"))

            val result = mutableMapOf<String, List<GoalieStatLeader>>()
            for (key in json.keys()) {
                val playersArray = json.getJSONArray(key)
                val players = mutableListOf<GoalieStatLeader>()
                for (i in 0 until playersArray.length()) {
                    val player = playersArray.getJSONObject(i)
                    players.add(
                        GoalieStatLeader(
                            id = player.getInt("id"),
                            firstName = player.getJSONObject("firstName").getString("default"),
                            lastName = player.getJSONObject("lastName").getString("default"),
                            sweaterNumber = player.optInt("sweaterNumber", -1).takeIf { it != -1 },
                            teamAbbrev = player.getString("teamAbbrev"),
                            value = player.getDouble("value"),
                        )
                    )
                }
                result[key] = players
            }
            result
        }

    companion object {
        /** [SavedStateHandle] key for the serialised in-progress game snapshot. */
        internal const val KEY_GAME_SNAPSHOT = "game_snapshot"
    }
}
