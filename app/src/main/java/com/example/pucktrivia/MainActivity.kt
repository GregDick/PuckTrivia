package com.example.pucktrivia

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.pucktrivia.model.SkaterStatLeader
import com.example.pucktrivia.ui.theme.PuckTriviaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private var statsData by mutableStateOf<Map<String, List<SkaterStatLeader>>>(emptyMap())
    private var isLoading by mutableStateOf(true)
    private var loadError by mutableStateOf(false)

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            try {
                val data = fetchSkaterStats()
                statsData = data
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to fetch stats", e)
                loadError = true
            } finally {
                isLoading = false
            }
        }

        setContent {
            PuckTriviaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        loadError -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Failed to load data. Please try again.",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        else -> {
                            TriviaQuestionScreen(
                                statsData = statsData,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
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
