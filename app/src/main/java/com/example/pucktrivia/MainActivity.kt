package com.example.pucktrivia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.pucktrivia.model.SkaterStatLeader
import com.example.pucktrivia.ui.theme.PuckTriviaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private val CATEGORY_ORDER =
    listOf(
        "points",
        "goals",
        "assists",
        "plusMinus",
        "penaltyMins",
        "goalsPp",
        "goalsSh",
        "faceoffLeaders",
        "toi",
    )

private val CATEGORY_LABELS =
    mapOf(
        "points" to "Points",
        "goals" to "Goals",
        "assists" to "Assists",
        "plusMinus" to "Plus/Minus",
        "penaltyMins" to "Penalty Minutes",
        "goalsPp" to "Power Play Goals",
        "goalsSh" to "Shorthanded Goals",
        "faceoffLeaders" to "Faceoff Leaders",
        "toi" to "Time on Ice",
    )

class MainActivity : ComponentActivity() {

    private var statsData by mutableStateOf<Map<String, List<SkaterStatLeader>>>(emptyMap())
    private var isLoading by mutableStateOf(true)

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val data = fetchSkaterStats()
            statsData = data
            isLoading = false
        }

        setContent {
            PuckTriviaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        TriviaQuestionScreen(
                            statsData = statsData,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
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

@Composable
fun StatsLeadersList(
    statsData: Map<String, List<SkaterStatLeader>>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        for (category in CATEGORY_ORDER) {
            val players = statsData[category] ?: continue
            val label = CATEGORY_LABELS[category] ?: category

            item {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
            }

            itemsIndexed(players) { index, player ->
                val rank = index + 1
                val displayValue =
                    if (player.value == player.value.toLong().toDouble()) {
                        player.value.toLong().toString()
                    } else {
                        player.value.toString()
                    }
                Text(
                    text =
                        "$rank. ${player.firstName} ${player.lastName} - ${player.teamAbbrev} - ${player.position} - $displayValue",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}
