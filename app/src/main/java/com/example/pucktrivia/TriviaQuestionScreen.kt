package com.example.pucktrivia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pucktrivia.model.SkaterStatLeader

private val CorrectGreen = Color(0xFF4CAF50)

@Composable
fun TriviaQuestionScreen(
    statsData: Map<String, List<SkaterStatLeader>>,
    modifier: Modifier = Modifier,
) {
    val pointsPlayers = statsData["points"]

    if (pointsPlayers.isNullOrEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Unable to load question")
        }
        return
    }

    var roundNumber by remember { mutableIntStateOf(0) }
    var usedPlayerIds by remember { mutableStateOf(emptySet<Int>()) }
    var selectedPlayerId by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }

    val choices =
        remember(roundNumber) {
            if (pointsPlayers.size - usedPlayerIds.size < 3) {
                usedPlayerIds = emptySet()
            }
            val available = pointsPlayers.filter { it.id !in usedPlayerIds }.shuffled().take(3)
            usedPlayerIds = usedPlayerIds + available.map { it.id }
            available
        }

    val correctPlayer = remember(choices) { choices.maxBy { it.value } }
    val answered = selectedPlayerId != null
    val isCorrect = selectedPlayerId == correctPlayer.id

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Score: $score",
            style = MaterialTheme.typography.titleMedium,
            color =
                when {
                    !answered -> MaterialTheme.colorScheme.onBackground
                    isCorrect -> CorrectGreen
                    else -> MaterialTheme.colorScheme.error
                },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )

        Text(
            text = "Which of these players currently has the most points?",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        if (answered) {
            Text(
                text = if (isCorrect) "Correct!" else "Incorrect!",
                style = MaterialTheme.typography.titleLarge,
                color = if (isCorrect) CorrectGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        choices.forEach { player ->
            val containerColor =
                when {
                    !answered -> MaterialTheme.colorScheme.primary
                    player.id == correctPlayer.id -> CorrectGreen
                    player.id == selectedPlayerId -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }

            Button(
                onClick = {
                    if (!answered) {
                        selectedPlayerId = player.id
                        if (player.id == correctPlayer.id) score += 100 else score = 0
                    }
                },
                enabled = !answered,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        disabledContainerColor = containerColor,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Text(
                    text = "${player.firstName} ${player.lastName}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (answered) {
            OutlinedButton(
                onClick = {
                    roundNumber++
                    selectedPlayerId = null
                },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Text(text = "Next", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
