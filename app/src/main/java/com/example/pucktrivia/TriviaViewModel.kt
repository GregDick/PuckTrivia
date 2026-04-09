package com.example.pucktrivia

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pucktrivia.di.IoDispatcher
import com.example.pucktrivia.di.StatsUrl
import com.example.pucktrivia.model.SkaterStatLeader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
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
    @StatsUrl private val statsUrl: String,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val random: kotlin.random.Random = kotlin.random.Random,
) : ViewModel() {

    var statsData by mutableStateOf<Map<String, List<SkaterStatLeader>>>(emptyMap())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var loadError by mutableStateOf(false)
        private set

    var score by mutableIntStateOf(0)
        private set

    var roundNumber by mutableIntStateOf(0)
        private set

    var pointsPool by mutableStateOf<List<SkaterStatLeader>>(emptyList())
        internal set

    var goalsPool by mutableStateOf<List<SkaterStatLeader>?>(null)
        internal set

    var pointsUsedIds by mutableStateOf(emptySet<Int>())
        internal set

    var goalsUsedIds by mutableStateOf(emptySet<Int>())
        internal set

    var selectedPlayerId by mutableStateOf<Int?>(null)
        private set

    var choices by mutableStateOf<List<SkaterStatLeader>>(emptyList())
        private set

    var correctPlayer by mutableStateOf<SkaterStatLeader?>(null)
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

    val answered: Boolean
        get() = selectedPlayerId != null

    val isCorrect: Boolean
        get() = selectedPlayerId == correctPlayer?.id

    init {
        fetchStats()
    }

    private fun fetchStats() {
        viewModelScope.launch {
            try {
                val data = fetchSkaterStats()
                statsData = data
                buildPools(data)
                prepareRound()
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
    }

    fun nextRound() {
        roundNumber++
        selectedPlayerId = null
        if (lives == 0) {
            gameOver = true
            return
        }
        prepareRound()
    }

    fun resetGame() {
        lives = 3
        score = 0
        totalAnswered = 0
        correctAnswered = 0
        selectedPlayerId = null
        gameOver = false
        pointsUsedIds = emptySet()
        goalsUsedIds = emptySet()
        prepareRound()
    }

    private fun buildPools(data: Map<String, List<SkaterStatLeader>>) {
        val pts = data["points"]
        if (pts != null) {
            val sorted = pts.sortedByDescending { it.value }
            pointsPool = sorted.take(kotlin.math.ceil(sorted.size / 2.0).toInt())
        }
        val gls = data["goals"]
        if (gls != null) {
            val sorted = gls.sortedByDescending { it.value }
            goalsPool = sorted.take(kotlin.math.ceil(sorted.size / 2.0).toInt())
        }
    }

    private fun prepareRound() {
        val pPool = pointsPool
        if (pPool.isEmpty()) return
        val gPool = goalsPool

        // Select question type
        val useGoals = gPool != null && random.nextBoolean()

        if (useGoals) {
            questionText = "Which of these players currently has the most goals?"
            statUnitLabel = "g"
        } else {
            questionText = "Which of these players currently has the most points?"
            statUnitLabel = "pts"
        }

        val pool = if (useGoals) gPool!! else pPool
        var usedIds = if (useGoals) goalsUsedIds else pointsUsedIds

        // Greedy no-tie pick of 3
        var picked = greedyPick(pool, usedIds)

        // If <3, reset this type's used set and retry
        if (picked.size < 3) {
            usedIds = emptySet()
            picked = greedyPick(pool, usedIds)
        }

        // Update the appropriate used set
        val newUsed = usedIds + picked.map { it.id }
        if (useGoals) {
            goalsUsedIds = newUsed
        } else {
            pointsUsedIds = newUsed
        }

        choices = picked
        correctPlayer = picked.maxByOrNull { it.value }
    }

    private fun greedyPick(
        pool: List<SkaterStatLeader>,
        usedIds: Set<Int>,
    ): List<SkaterStatLeader> {
        val unused = pool.filter { it.id !in usedIds }.shuffled(random)
        val claimedValues = mutableSetOf<Double>()
        val result = mutableListOf<SkaterStatLeader>()
        for (player in unused) {
            if (player.value !in claimedValues) {
                claimedValues.add(player.value)
                result.add(player)
                if (result.size == 3) break
            }
        }
        return result
    }

    private suspend fun fetchSkaterStats(): Map<String, List<SkaterStatLeader>> =
        withContext(ioDispatcher) {
            val request = Request.Builder().url(statsUrl).build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body!!.string())

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
}
