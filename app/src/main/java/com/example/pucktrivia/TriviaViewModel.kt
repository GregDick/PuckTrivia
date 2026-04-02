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

    var usedPlayerIds by mutableStateOf(emptySet<Int>())
        private set

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
        if (playerId == correctPlayer?.id) {
            score += 100
        } else {
            score = 0
        }
    }

    fun nextRound() {
        roundNumber++
        selectedPlayerId = null
        prepareRound()
    }

    private fun prepareRound() {
        val pointsPlayers = statsData["points"] ?: return
        val goalsPlayers = statsData["goals"]

        // Check if either pool needs a reset (use raw counts, not post-dedup)
        var currentUsed = usedPlayerIds
        val pointsUnusedCount = pointsPlayers.count { it.id !in currentUsed }
        val goalsUnusedCount = goalsPlayers?.count { it.id !in currentUsed } ?: Int.MAX_VALUE
        if (pointsUnusedCount < 3 || goalsUnusedCount < 3) {
            currentUsed = emptySet()
        }

        // Select question type: true = goals, false = points
        val useGoals = goalsPlayers != null && random.nextBoolean()
        val selectedPlayers = if (useGoals) goalsPlayers!! else pointsPlayers

        if (useGoals) {
            questionText = "Which of these players currently has the most goals?"
            statUnitLabel = "g"
        } else {
            questionText = "Which of these players currently has the most points?"
            statUnitLabel = "pts"
        }

        val available =
            selectedPlayers
                .filter { it.id !in currentUsed }
                .shuffled()
                .distinctBy { it.value }
                .take(3)
        usedPlayerIds = currentUsed + available.map { it.id }
        choices = available
        correctPlayer = available.maxBy { it.value }
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
