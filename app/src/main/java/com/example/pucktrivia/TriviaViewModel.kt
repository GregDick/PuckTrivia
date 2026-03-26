package com.example.pucktrivia

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pucktrivia.model.SkaterStatLeader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@HiltViewModel
class TriviaViewModel @Inject constructor(private val client: OkHttpClient) : ViewModel() {

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
        if (answered) return
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
        var currentUsed = usedPlayerIds
        if (pointsPlayers.size - currentUsed.size < 3) {
            currentUsed = emptySet()
        }
        val available = pointsPlayers.filter { it.id !in currentUsed }.shuffled().take(3)
        usedPlayerIds = currentUsed + available.map { it.id }
        choices = available
        correctPlayer = available.maxBy { it.value }
    }

    private suspend fun fetchSkaterStats(): Map<String, List<SkaterStatLeader>> =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url("https://api-web.nhle.com/v1/skater-stats-leaders/current?limit=-1")
                    .build()

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
