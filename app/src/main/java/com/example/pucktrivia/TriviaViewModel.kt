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
import com.example.pucktrivia.model.QuestionType
import com.example.pucktrivia.model.SkaterStatLeader
import com.example.pucktrivia.model.positionGroup
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

    var pools by mutableStateOf<Map<QuestionType, List<SkaterStatLeader>>>(emptyMap())
        internal set

    var usedIds by mutableStateOf<Map<QuestionType, Set<Int>>>(emptyMap())
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

    var fatalError by mutableStateOf(false)
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
        fatalError = false
        usedIds = emptyMap()
        prepareRound()
    }

    private fun buildPools(data: Map<String, List<SkaterStatLeader>>) {
        val built = mutableMapOf<QuestionType, List<SkaterStatLeader>>()
        for (type in QuestionType.entries) {
            val players = data[type.statKey] ?: continue
            val group = players.filter { it.positionGroup() == type.positionGroup }
            if (group.isEmpty()) continue
            val sorted = group.sortedByDescending { it.value }
            built[type] = sorted.take(kotlin.math.ceil(sorted.size / 2.0).toInt())
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
        // Pool is structurally unviable (too few distinct values even after reset); halt rather
        // than loop or silently fall back to another pool type.
        if (picked.size < 3) {
            Log.e(
                "TriviaViewModel",
                "Pool for $type cannot produce 3 distinct choices even after reset " +
                    "(poolSize=${pool.size}, distinctValues=${pool.distinctBy { it.value }.size})",
            )
            fatalError = true
            return
        }

        questionText = type.questionText
        statUnitLabel = type.unitLabel
        usedIds = usedIds + (type to (currentUsed + picked.map { it.id }))
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
